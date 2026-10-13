package com.parroto.subscription.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** VNPay IPN acknowledgement body: {@code {"RspCode":"00","Message":"Confirm Success"}}. */
public record VnpayIpnResponse(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {
}
