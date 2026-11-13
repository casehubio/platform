package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotPathExpressionEngineTest {
    @Test
    void resolvesMapFieldsAndListIndexes() {
        var context = Map.of("items", List.of(Map.of("id", "first"), Map.of("id", "second")),
                             "nested", Map.of("value", "preserved"));
        var engine = new DotPathExpressionEngine();
        assertThat(engine.compile("items[0].id", Map.class, String.class).eval(context)).isEqualTo("first");
        assertThat(engine.compile("items[1].id", Map.class, String.class).eval(context)).isEqualTo("second");
        assertThat(engine.compile("nested.value", Map.class, String.class).eval(context)).isEqualTo("preserved");
        for (String path : List.of("items[2].id", "items[-1].id", "items[999999999999999999999].id",
                                   "items[other].id", "missing.id")) {
            assertThat(engine.compile(path, Map.class, String.class).eval(context)).as(path).isNull();
        }
    }
}
