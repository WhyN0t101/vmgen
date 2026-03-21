package io.vmgen.api.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable wrapper for paginated responses returned to clients. Avoids exposing
 * Spring Data's PageImpl JSON structure directly.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PagedResponse<T> from(org.springframework.data.domain.Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
    public static <T, R> PagedResponse<R> from(Page<T> page, List<R> mappedContent) {
        return new PagedResponse<>(mappedContent, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}