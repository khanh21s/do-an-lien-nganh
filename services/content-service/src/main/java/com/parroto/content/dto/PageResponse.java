package com.parroto.content.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> data, PageInfo pagination) {

    public record PageInfo(int page, int limit, long total, int totalPages) {
    }

    public static <T> PageResponse<T> of(Page<T> page, int uiPage, int limit) {
        return new PageResponse<>(page.getContent(),
                new PageInfo(uiPage, limit, page.getTotalElements(), page.getTotalPages()));
    }
}
