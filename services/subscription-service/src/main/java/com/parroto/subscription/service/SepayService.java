package com.parroto.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * SePay payment gateway (https://developer.sepay.vn/vi/cong-thanh-toan): money goes straight to the
 * merchant's own bank account (VietQR / NAPAS transfer).
 * <ul>
 *   <li>Checkout: the browser POSTs a signed form to {@code <checkout>/init}. Signature = Base64(HMAC-SHA256)
 *       over {@code "field=value,field=value"} in form order (same as the official sepay-pg-node SDK).</li>
 *   <li>Result: the success/error/cancel redirects carry no signature, so the order status is always read
 *       back server-to-server from the REST API ({@code GET /order/detail/{invoice}}, Basic auth). This also
 *       works on localhost, where SePay cannot reach the IPN endpoint.</li>
 *   <li>IPN: POST JSON with header {@code X-Secret-Key} = merchant secret key.</li>
 * </ul>
 */
@Service
public class SepayService {

    private static final Logger log = LoggerFactory.getLogger(SepayService.class);

    /** order_status of a paid order. */
    public static final String ORDER_CAPTURED = "CAPTURED";
    public static final String ORDER_CANCELLED = "CANCELLED";

    private final String merchantId;
    private final String secretKey;
    private final String checkoutBaseUrl;
    private final String apiBaseUrl;
    private final String publicApiUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public SepayService(@Value("${sepay.env}") String env,
                        @Value("${sepay.merchant-id}") String merchantId,
                        @Value("${sepay.secret-key}") String secretKey,
                        @Value("${sepay.public-api-url}") String publicApiUrl,
                        RestTemplate restTemplate) {
        boolean production = "production".equalsIgnoreCase(env == null ? "" : env.trim());
        this.merchantId = merchantId == null ? "" : merchantId.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.checkoutBaseUrl = production ? "https://pay.sepay.vn/v1/checkout" : "https://pay-sandbox.sepay.vn/v1/checkout";
        this.apiBaseUrl = production ? "https://pgapi.sepay.vn/v1" : "https://pgapi-sandbox.sepay.vn/v1";
        this.publicApiUrl = stripTrailingSlash(publicApiUrl);
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return !merchantId.isBlank() && !secretKey.isBlank();
    }

    /** URL the frontend sends the browser to; it renders the auto-submitting SePay form. */
    public String checkoutPageUrl(String invoiceNumber) {
        return publicApiUrl + "/subscriptions/sepay/checkout/" + invoiceNumber;
    }

    public String checkoutInitUrl() {
        return checkoutBaseUrl + "/init";
    }

    /** Signed checkout form fields, in the order they must be posted (the signature depends on it). */
    public Map<String, String> checkoutFields(String invoiceNumber, long amountVnd, String description) {
        String returnUrl = publicApiUrl + "/subscriptions/sepay/return?txn=" + invoiceNumber + "&result=";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant", merchantId);
        fields.put("operation", "PURCHASE");
        fields.put("payment_method", "BANK_TRANSFER");
        fields.put("order_invoice_number", invoiceNumber);
        fields.put("order_amount", String.valueOf(amountVnd));
        fields.put("currency", "VND");
        fields.put("order_description", VNPayService.toVnpayText(description));
        fields.put("success_url", returnUrl + "success");
        fields.put("error_url", returnUrl + "error");
        fields.put("cancel_url", returnUrl + "cancel");
        fields.put("signature", sign(fields));
        return fields;
    }

    /** Base64(HMAC-SHA256(secret, "k1=v1,k2=v2,...")) over every field except {@code signature}, in map order. */
    String sign(Map<String, String> fields) {
        StringJoiner data = new StringJoiner(",");
        fields.forEach((k, v) -> {
            if (!"signature".equals(k) && v != null) {
                data.add(k + "=" + v);
            }
        });
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SePay signature", e);
        }
    }

    /** IPN authenticity: constant-time comparison of the {@code X-Secret-Key} header. */
    public boolean isValidIpnKey(String headerValue) {
        if (!isConfigured() || headerValue == null) {
            return false;
        }
        return MessageDigest.isEqual(secretKey.getBytes(StandardCharsets.UTF_8),
                headerValue.trim().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads the order from SePay's REST API.
     *
     * @return the order, or {@code null} when SePay does not know it (404) or cannot be reached
     */
    public SepayOrder fetchOrder(String invoiceNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(merchantId, secretKey, StandardCharsets.UTF_8);
        try {
            Map<String, Object> body = restTemplate.exchange(apiBaseUrl + "/order/detail/" + invoiceNumber,
                    HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() { }).getBody();
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof Map<?, ?> order)) {
                return null;
            }
            return new SepayOrder(String.valueOf(order.get("order_status")), parseAmount(order.get("order_amount")));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                log.warn("SePay order lookup for {} failed: {} {}", invoiceNumber, e.getStatusCode(), e.getResponseBodyAsString());
            }
            return null;
        } catch (RestClientException e) {
            log.warn("SePay order lookup for {} failed: {}", invoiceNumber, e.getMessage());
            return null;
        }
    }

    private static Long parseAmount(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static String stripTrailingSlash(String url) {
        String u = url == null ? "" : url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    /** @param amount whole VND ("79000.00" -> 79000), null if unparseable */
    public record SepayOrder(String status, Long amount) {
        public boolean isPaid() {
            return ORDER_CAPTURED.equals(status);
        }
    }
}
