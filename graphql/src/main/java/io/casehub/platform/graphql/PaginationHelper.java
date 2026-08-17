package io.casehub.platform.graphql;

import java.util.List;
import java.util.function.Function;

public final class PaginationHelper {

    private PaginationHelper() {}

    public static <S, T> PageResult<T> paginate(
            List<S> all, PageInput page, Function<S, T> mapper) {
        int offset = page != null && page.offset() != null ? page.offset() : 0;
        int limit = page != null && page.limit() != null ? page.limit() : 20;
        int total = all.size();
        int end = Math.min(offset + limit, total);

        List<T> items = offset < total
                ? all.subList(offset, end).stream().map(mapper).toList()
                : List.of();

        return new PageResult<>(items, new PageInfo(
                end < total, offset > 0, total, null));
    }
}
