package io.casehub.platform.graphql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageInputTest {

    @Test
    void defaultsToNullFields() {
        var page = new PageInput(null, null, null);
        assertThat(page.offset()).isNull();
        assertThat(page.limit()).isNull();
        assertThat(page.cursor()).isNull();
    }

    @Test
    void carriesOffsetAndLimit() {
        var page = new PageInput(0, 25, null);
        assertThat(page.offset()).isEqualTo(0);
        assertThat(page.limit()).isEqualTo(25);
        assertThat(page.cursor()).isNull();
    }

    @Test
    void carriesCursorForKeysetPagination() {
        var page = new PageInput(null, 10, "abc123");
        assertThat(page.cursor()).isEqualTo("abc123");
    }

    @Test
    void recordEqualityByValue() {
        var a = new PageInput(0, 25, null);
        var b = new PageInput(0, 25, null);
        assertThat(a).isEqualTo(b);
    }
}
