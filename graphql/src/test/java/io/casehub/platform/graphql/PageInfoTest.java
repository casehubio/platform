package io.casehub.platform.graphql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageInfoTest {

    @Test
    void hasNextAndHasPrevious() {
        var info = new PageInfo(true, false, 100, "cursor-xyz");
        assertThat(info.hasNext()).isTrue();
        assertThat(info.hasPrevious()).isFalse();
        assertThat(info.totalCount()).isEqualTo(100);
        assertThat(info.cursor()).isEqualTo("cursor-xyz");
    }

    @Test
    void totalCountNullableWhenUnknown() {
        var info = new PageInfo(false, false, null, null);
        assertThat(info.totalCount()).isNull();
        assertThat(info.cursor()).isNull();
    }

    @Test
    void recordEqualityByValue() {
        var a = new PageInfo(true, true, 50, "c1");
        var b = new PageInfo(true, true, 50, "c1");
        assertThat(a).isEqualTo(b);
    }
}
