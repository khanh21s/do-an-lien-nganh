package com.parroto.subscription.service;

import com.parroto.subscription.dto.VnpayIpnResponse;

/**
 * Outcome of processing a VNPay result (IPN or return URL).
 *
 * @param rspCode           VNPay IPN code: 00 ok, 01 order not found, 02 already processed,
 *                          04 invalid amount, 97 invalid signature, 99 other error
 * @param message           VNPay IPN message
 * @param transactionStatus status of the payment_transaction after processing (null when unknown)
 */
public record VnpayResult(String rspCode, String message, String transactionStatus) {

    public static final String OK = "00";
    public static final String ORDER_NOT_FOUND = "01";
    public static final String ALREADY_CONFIRMED = "02";
    public static final String INVALID_AMOUNT = "04";
    public static final String INVALID_SIGNATURE = "97";
    public static final String UNKNOWN_ERROR = "99";

    static VnpayResult of(String code, String message, String transactionStatus) {
        return new VnpayResult(code, message, transactionStatus);
    }

    public VnpayIpnResponse toIpnResponse() {
        return new VnpayIpnResponse(rspCode, message);
    }
}
