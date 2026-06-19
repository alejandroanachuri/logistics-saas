package ar.com.logistics.auth.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Generic envelope for paginated list responses. Wraps a
 * Spring-Data {@link Page} into the {@code {data, total, page,
 * size}} wire-format the frontend expects.
 *
 * <p>The {@code data} field is the page's content (so callers don't
 * need to read {@code content} / {@code pageable.offset}). The
 * {@code total} is the unpaginated total row count. The {@code page}
 * is the zero-indexed page number. The {@code size} is the page
 * size that was applied.
 *
 * <p>The {@code static of(Page)} factory converts a Spring-Data
 * {@code Page<T>} directly into the wire format — used by the
 * {@code CompanyUsersController.list} handler.
 */
public record PageResponse<T>(List<T> data, long total, int page, int size) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
    }

    /** Convenience for tests / non-paginated sources. */
    public static <T> PageResponse<T> of(List<T> rows, long total, int page, int size) {
        return new PageResponse<>(List.copyOf(rows), total, page, size);
    }
}
