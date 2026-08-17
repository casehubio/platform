package io.casehub.platform.graphql;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.assertThat;

class PaginationHelperTest {

    private final List<String> items = List.of("a", "b", "c", "d", "e");

    @Test
    void paginate_firstPage() {
        var result = PaginationHelper.paginate(items,
                new PageInput(0, 2, null), Function.identity());
        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.pageInfo().hasNext()).isTrue();
        assertThat(result.pageInfo().hasPrevious()).isFalse();
        assertThat(result.pageInfo().totalCount()).isEqualTo(5);
    }

    @Test
    void paginate_middlePage() {
        var result = PaginationHelper.paginate(items,
                new PageInput(2, 2, null), Function.identity());
        assertThat(result.items()).containsExactly("c", "d");
        assertThat(result.pageInfo().hasNext()).isTrue();
        assertThat(result.pageInfo().hasPrevious()).isTrue();
    }

    @Test
    void paginate_lastPage() {
        var result = PaginationHelper.paginate(items,
                new PageInput(4, 2, null), Function.identity());
        assertThat(result.items()).containsExactly("e");
        assertThat(result.pageInfo().hasNext()).isFalse();
        assertThat(result.pageInfo().hasPrevious()).isTrue();
    }

    @Test
    void paginate_nullPage_usesDefaults() {
        var result = PaginationHelper.paginate(items, null, Function.identity());
        assertThat(result.items()).containsExactly("a", "b", "c", "d", "e");
        assertThat(result.pageInfo().totalCount()).isEqualTo(5);
        assertThat(result.pageInfo().hasNext()).isFalse();
        assertThat(result.pageInfo().hasPrevious()).isFalse();
    }

    @Test
    void paginate_emptyList() {
        var result = PaginationHelper.paginate(List.of(), null, Function.identity());
        assertThat(result.items()).isEmpty();
        assertThat(result.pageInfo().totalCount()).isEqualTo(0);
        assertThat(result.pageInfo().hasNext()).isFalse();
        assertThat(result.pageInfo().hasPrevious()).isFalse();
    }

    @Test
    void paginate_withMapper() {
        var result = PaginationHelper.paginate(items,
                new PageInput(0, 3, null), String::toUpperCase);
        assertThat(result.items()).containsExactly("A", "B", "C");
    }

    @Test
    void paginate_offsetBeyondEnd() {
        var result = PaginationHelper.paginate(items,
                new PageInput(10, 2, null), Function.identity());
        assertThat(result.items()).isEmpty();
        assertThat(result.pageInfo().hasPrevious()).isTrue();
        assertThat(result.pageInfo().hasNext()).isFalse();
    }

    @Test
    void paginate_exactBoundary() {
        var result = PaginationHelper.paginate(items,
                new PageInput(3, 2, null), Function.identity());
        assertThat(result.items()).containsExactly("d", "e");
        assertThat(result.pageInfo().hasNext()).isFalse();
        assertThat(result.pageInfo().hasPrevious()).isTrue();
    }

    @Test
    void paginate_nullOffsetAndLimit_usesDefaults() {
        var result = PaginationHelper.paginate(items,
                new PageInput(null, null, null), Function.identity());
        assertThat(result.items()).hasSize(5);
    }
}
