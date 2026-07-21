package com.jobcopilot.dto.common;

import java.util.List;
import java.util.function.Function;

public record CursorPageResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasMore,
        int size
) {
    public static <E, T> CursorPageResponse<T> from(List<E> items, int limit, Function<E, T> mapper) {
        boolean hasMore = items.size() > limit;
        List<E> page = hasMore ? items.subList(0, limit) : items;
        E last = page.isEmpty() ? null : page.get(page.size() - 1);
        String nextCursor = last != null && hasMore ? encodeCursor(last) : null;
        return new CursorPageResponse<>(
                page.stream().map(mapper).toList(),
                nextCursor,
                hasMore,
                page.size()
        );
    }

    public static String encodeCursor(Object entity) {
        if (entity instanceof com.jobcopilot.entity.Posting p) {
            return p.getScore() + "_" + p.getId();
        }
        throw new IllegalArgumentException("Unsupported entity type for cursor encoding");
    }
}
