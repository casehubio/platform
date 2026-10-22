package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMapTest {

    @Test
    void empty_returnsEmptyOptional() {
        assertThat(SourceMap.empty().locate("any")).isEmpty();
    }

    @Test
    void builder_recordsAndRetrieves() {
        var builder = new SourceMap.Builder();
        builder.record("rest_0", "/steps/0", 0);
        builder.record("fork_1", "/steps/1", 1);

        SourceMap map = builder.build();
        assertThat(map.locate("rest_0")).isPresent();
        assertThat(map.locate("rest_0").get().yamlPath()).isEqualTo("/steps/0");
        assertThat(map.locate("rest_0").get().stepIndex()).isEqualTo(0);
        assertThat(map.locate("fork_1")).isPresent();
        assertThat(map.locate("unknown")).isEmpty();
    }

    @Test
    void builder_producesImmutableMap() {
        var builder = new SourceMap.Builder();
        builder.record("a", "/0", 0);
        SourceMap map = builder.build();
        assertThat(map.entries()).isUnmodifiable();
    }
}
