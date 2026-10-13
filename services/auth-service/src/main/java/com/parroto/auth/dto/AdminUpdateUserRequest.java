package com.parroto.auth.dto;

import jakarta.validation.constraints.NotNull;

public record AdminUpdateUserRequest(
        @NotNull(message = "isActive là bắt buộc") Boolean isActive
) {
}
