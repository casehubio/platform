package io.casehub.yaml.step.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordsTest {

    record Simple(String name, int value) {}

    record Nested(String label, Simple inner) {}

    record WithList(String id, List<Simple> items) {}

    record WithOptional(String id, Optional<String> note) {}

    record WithMap(String id, Map<String, Object> data) {}

    record WithNullComponent(String required, String nullable) {}

    record WithSimpleList(String id, List<String> tags) {}

    @Test
    void simple_record() {
        var result = Records.toMap(new Simple("hello", 42));
        assertThat(result).containsEntry("name", "hello").containsEntry("value", 42);
    }

    @Test
    void nested_record_flattens_recursively() {
        var result = Records.toMap(new Nested("outer", new Simple("inner", 7)));
        assertThat(result).containsKey("inner");
        assertThat(result.get("inner")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        var inner = (Map<String, Object>) result.get("inner");
        assertThat(inner).containsEntry("name", "inner").containsEntry("value", 7);
    }

    @Test
    void list_of_records_flattened() {
        var result = Records.toMap(new WithList("x",
                List.of(new Simple("a", 1), new Simple("b", 2))));
        assertThat(result.get("items")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("name", "a");
    }

    @Test
    void list_of_simple_types_passed_through() {
        var result = Records.toMap(new WithSimpleList("x", List.of("tag1", "tag2")));
        assertThat(result.get("tags")).isEqualTo(List.of("tag1", "tag2"));
    }

    @Test
    void optional_present_unwrapped() {
        var result = Records.toMap(new WithOptional("x", Optional.of("note-text")));
        assertThat(result).containsEntry("note", "note-text");
    }

    @Test
    void optional_empty_becomes_null() {
        var result = Records.toMap(new WithOptional("x", Optional.empty()));
        assertThat(result).containsKey("note");
        assertThat(result.get("note")).isNull();
    }

    @Test
    void map_component_passed_through() {
        var data = Map.<String, Object>of("key", "value");
        var result = Records.toMap(new WithMap("x", data));
        assertThat(result.get("data")).isEqualTo(data);
    }

    @Test
    void null_component_value_preserved() {
        var result = Records.toMap(new WithNullComponent("req", null));
        assertThat(result).containsEntry("required", "req");
        assertThat(result).containsKey("nullable");
        assertThat(result.get("nullable")).isNull();
    }

    @Test
    void null_input_throws() {
        assertThatThrownBy(() -> Records.toMap(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void non_record_input_throws() {
        assertThatThrownBy(() -> Records.toMap("not a record"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
