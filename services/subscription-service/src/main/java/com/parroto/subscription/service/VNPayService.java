package com.parroto.subscription.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final String payUrl;
    private final String tmnCode;
    private final String hashSecret;
    private final String returnUrl;

    public VNPayService(@Value("${vnpay.pay-url}") String payUrl,
                         @Value("${vnpay.tmn-code}") String tmnCode,
                         @Value("${vnpay.hash-secret}") String hashSecret,
                         @Value("${vnpay.return-url}") String returnUrl) {
        this.payUrl = payUrl;
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.returnUrl = returnUrl;
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
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress == null || ipAddress.isBlank() ? "127.0.0.1" : ipAddress);
        params.put("vnp_CreateDate", LocalDateTime.now().format(DATE_FORMAT));

        String hashData = buildHashData(params);
        String secureHash = hmacSha512(hashSecret, hashData);

        StringBuilder query = new StringBuilder(payUrl).append('?').append(buildQueryString(params));
        query.append("&vnp_SecureHash=").append(secureHash);
        return query.toString();
    }

    /**
     * @param params all query parameters VNPay sent back, including vnp_SecureHash
     */
    public boolean verifyCallback(Map<String, String> params) {
        if (!isConfigured()) {
            return false;
        }
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null) {
            return false;
        }
        Map<String, String> toVerify = new TreeMap<>(params);
        toVerify.remove("vnp_SecureHash");
        toVerify.remove("vnp_SecureHashType");

        String hashData = buildHashData(toVerify);
        String computedHash = hmacSha512(hashSecret, hashData);
        return computedHash.equalsIgnoreCase(receivedHash);
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
