package io.casehub.platform.graphql.scalar;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonScalarAdapterTest {

    @Test
    void constructsFromMap() {
        var map  = Map.<String, Object>of("name", "test", "count", 42);
        var json = Json.of(map);
        assertThat(json.value()).containsEntry("name", "test");
        assertThat(json.value()).containsEntry("count", 42);
    }

    @Test
    void constructsFromJsonString() {
        var json = new Json("{\"name\":\"test\",\"count\":42}");
        assertThat(json.value()).containsEntry("name", "test");
        assertThat(json.value()).containsEntry("count", 42);
    }

    @Test
    void serializesToJsonString() {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", "test");
        map.put("count", 42);
        var json = Json.of(map);

        String result = json.toString();
        assertThat(result).contains("\"name\"");
        assertThat(result).contains("\"test\"");
        assertThat(result).contains("42");
    }

    @Test
    void roundTripsNestedStructure() {
        var inner    = Map.<String, Object>of("key", "value");
        var original = Map.<String, Object>of("nested", inner, "list", List.of(1, 2, 3));

        var    json         = Json.of(original);
        String serialized   = json.toString();
        var    roundTripped = new Json(serialized);

        assertThat(roundTripped.value()).containsKey("nested");
        assertThat(roundTripped.value()).containsKey("list");
        @SuppressWarnings("unchecked")
        var nestedResult = (Map<String, Object>) roundTripped.value().get("nested");
        assertThat(nestedResult).containsEntry("key", "value");
    }

    @Test
    void nullMapProducesNullValue() {
        var json = Json.of(null);
        assertThat(json.value()).isNull();
        assertThat(json.toString()).isEqualTo("null");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> new Json("not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyObjectRoundTrips() {
        var json = new Json("{}");
        assertThat(json.value()).isEmpty();
    }

    @Test
    void ofStringFactoryDelegates() {
        var json = Json.ofString("{\"a\":1}");
        assertThat(json.value()).containsEntry("a", 1);
    }
}
