package com.ebee.common.result;

import org.springframework.data.domain.Page;

public record PageResult<T>(
        java.util.List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean success,
        String message
) {

    public static <T> PageResult<T> ok(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                true,
                null
        );
    }

    public static <T> PageResult<T> fail(String message) {
        return new PageResult<>(null, 0, 0, 0, 0, false, message);
    }
}
