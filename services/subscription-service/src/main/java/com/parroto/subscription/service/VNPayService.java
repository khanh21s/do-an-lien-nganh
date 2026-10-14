package com.parroto.subscription.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds VNPay payment URLs and verifies IPN/return callback signatures, following
 * VNPay's official integration spec: sorted query params, HMAC-SHA512 over the
 * URL-encoded "key=value&..." string, hex-encoded, everything except vnp_SecureHash
 * (and vnp_SecureHashType) included in the signed data.
 * https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
 */
@Service
public class VNPayService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** VNPay reads vnp_CreateDate / vnp_ExpireDate as GMT+7, whatever the server's timezone (docker = UTC). */
    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Payment link lifetime, same as CreatePaymentResponse.expiresIn. */
    public static final long PAYMENT_TTL_MINUTES = 15;

    private final String payUrl;
    private final String tmnCode;
    private final String hashSecret;
    private final String returnUrl;
    private final Clock clock;

    @Autowired
    public VNPayService(@Value("${vnpay.pay-url}") String payUrl,
                         @Value("${vnpay.tmn-code}") String tmnCode,
                         @Value("${vnpay.hash-secret}") String hashSecret,
                         @Value("${vnpay.return-url}") String returnUrl) {
        this(payUrl, tmnCode, hashSecret, returnUrl, Clock.systemUTC());
    }

    VNPayService(String payUrl, String tmnCode, String hashSecret, String returnUrl, Clock clock) {
        this.payUrl = payUrl;
        this.tmnCode = tmnCode == null ? null : tmnCode.trim();
        this.hashSecret = hashSecret == null ? null : hashSecret.trim();
        this.returnUrl = returnUrl;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
    }

    public String buildPaymentUrl(long amountVnd, String txnRef, String orderInfo, String ipAddress) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amountVnd * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", toVnpayText(orderInfo));
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress == null || ipAddress.isBlank() ? "127.0.0.1" : ipAddress);
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(VNPAY_ZONE);
        params.put("vnp_CreateDate", now.format(DATE_FORMAT));
        params.put("vnp_ExpireDate", now.plusMinutes(PAYMENT_TTL_MINUTES).format(DATE_FORMAT));

        String secureHash = sign(params);

        StringBuilder query = new StringBuilder(payUrl).append('?').append(buildQueryString(params));
        query.append("&vnp_SecureHash=").append(secureHash);
        return query.toString();
    }

    /**
     * Pure signature check (SUB-01): recomputes HMAC-SHA512 over every {@code vnp_*} parameter
     * except {@code vnp_SecureHash}/{@code vnp_SecureHashType} and compares in constant time.
     *
     * @param params all query parameters VNPay sent back, including vnp_SecureHash
     */
    public boolean verifySignature(Map<String, String> params) {
        if (!isConfigured() || params == null) {
            return false;
        }
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        String computedHash = sign(params);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.US_ASCII),
                receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Computes the hex HMAC-SHA512 VNPay signature of {@code params} (only {@code vnp_*} keys,
     * sorted, excluding the hash fields themselves). Used to sign outgoing URLs and in tests.
     */
    public String sign(Map<String, String> params) {
        Map<String, String> toSign = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k != null && k.startsWith("vnp_") && !"vnp_SecureHash".equals(k) && !"vnp_SecureHashType".equals(k)) {
                toSign.put(k, v);
            }
        });
        return hmacSha512(hashSecret, buildHashData(toSign));
    }

    /**
     * VNPay requires vnp_OrderInfo in Vietnamese without diacritics and without special
     * characters ("Nâng cấp gói 1 tháng" -> "Nang cap goi 1 thang").
     */
    static String toVnpayText(String text) {
        if (text == null) {
            return "";
        }
        String ascii = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return ascii.replaceAll("[^A-Za-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String buildHashData(Map<String, String> sortedParams) {
        return buildQueryString(sortedParams);
    }

    private String buildQueryString(Map<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            sb.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute VNPay HMAC signature", e);
        }
    }
}
