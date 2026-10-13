package com.parroto.subscription.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /subscriptions/admin/plans} and {@code PUT /subscriptions/admin/plans/{id}} (T14-SUB).
 *
 * @param id       required on create ({@code ^[a-z0-9-]{3,50}$}); ignored on update (the path id wins)
 * @param features feature strings; blank entries are dropped, null = empty list
 * @param isActive null = true on create, unchanged on update
 */
public record AdminPlanRequest(
        @Pattern(regexp = ID_PATTERN, message = "Mã gói chỉ gồm a-z, 0-9, dấu '-' và dài 3–50 ký tự")
        String id,

        @NotBlank(message = "Tên gói là bắt buộc")
        @Size(max = 255, message = "Tên gói tối đa 255 ký tự")
        String name,

        @NotNull(message = "Thời hạn là bắt buộc")
        @Min(value = 1, message = "Thời hạn phải từ 1 đến 3650 ngày")
        @Max(value = 3650, message = "Thời hạn phải từ 1 đến 3650 ngày")
        Integer durationDays,

        @NotNull(message = "Giá là bắt buộc")
        @Min(value = 1000, message = "Giá tối thiểu là 1.000 ₫")
        Long price,

        @Size(max = 20, message = "Tối đa 20 quyền lợi")
        List<@Size(max = 255, message = "Mỗi quyền lợi tối đa 255 ký tự") String> features,

        @JsonProperty("isActive")
        Boolean isActive
) {
    public static final String ID_PATTERN = "^[a-z0-9-]{3,50}$";
}
