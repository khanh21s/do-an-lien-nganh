package com.parroto.practice.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String error,
        int status,
        OffsetDateTime timestamp,
        String requestId
) {
    public static ErrorResponse of(String error, int status, String requestId) {
        return new ErrorResponse(error, status, OffsetDateTime.now(), requestId);
    }
}
