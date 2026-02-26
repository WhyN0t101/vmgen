package io.vmgen.api.dto.common;

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
}

