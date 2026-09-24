package io.casehub.yaml.core.type;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedVariablesTest {

    @Test
    void parse_typed_variables() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("batch_size:integer", 500);
        raw.put("bucket", "prod");
        raw.put("enabled:boolean", true);
        raw.put("ratio:number", 3.14);

        TypedMap result = TypedVariables.parse(raw);

        assertThat(result.typeOf("batch_size")).isEqualTo(ValueType.INTEGER);
        assertThat(result.typeOf("bucket")).isEqualTo(ValueType.STRING);
        assertThat(result.typeOf("enabled")).isEqualTo(ValueType.BOOLEAN);
        assertThat(result.typeOf("ratio")).isEqualTo(ValueType.NUMBER);

        assertThat(result.values().get("batch_size")).isEqualTo(500);
        assertThat(result.values().get("bucket")).isEqualTo("prod");
        assertThat(result.values().get("enabled")).isEqualTo(true);
        assertThat(result.values().get("ratio")).isEqualTo(3.14);
    }

    @Test
    void parse_untyped_defaults_to_string() {
        TypedMap result = TypedVariables.parse(Map.of("name", "hello"));

        assertThat(result.typeOf("name")).isEqualTo(ValueType.STRING);
        assertThat(result.values().get("name")).isEqualTo("hello");
    }

    @Test
    void parse_invalid_value_includes_variable_name() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("count:integer", "not-a-number");

        assertThatThrownBy(() -> TypedVariables.parse(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count")
                .hasMessageContaining("INTEGER")
                .hasMessageContaining("not-a-number");
    }

    @Test
    void parse_empty_map_returns_empty() {
        TypedMap result = TypedVariables.parse(Map.of());
        assertThat(result.schema()).isEmpty();
        assertThat(result.values()).isEmpty();
    }

    @Test
    void schema_implements_TypedSchema() {
        TypedMap result = TypedVariables.parse(Map.of("x:integer", 42));
        assertThat(result).isInstanceOf(TypedSchema.class);
        assertThat(result.schema()).containsEntry("x", ValueType.INTEGER);
    }

    @Test
    void values_are_unmodifiable() {
        TypedMap result = TypedVariables.parse(Map.of("x", "hello"));
        assertThatThrownBy(() -> result.values().put("y", "world"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
