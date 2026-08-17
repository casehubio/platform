package io.casehub.platform.api.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlMergerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode json(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void overlayKeyOverridesBaseKey() throws Exception {
        JsonNode base = json("{\"a\": 1, \"b\": 2}");
        JsonNode overlay = json("{\"b\": 3}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("a").asInt()).isEqualTo(1);
        assertThat(result.get("b").asInt()).isEqualTo(3);
    }

    @Test
    void baseKeysPreservedWhenNotInOverlay() throws Exception {
        JsonNode base = json("{\"a\": 1, \"b\": 2}");
        JsonNode overlay = json("{\"c\": 3}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("a").asInt()).isEqualTo(1);
        assertThat(result.get("b").asInt()).isEqualTo(2);
        assertThat(result.get("c").asInt()).isEqualTo(3);
    }

    @Test
    void nestedObjectsMergeRecursively() throws Exception {
        JsonNode base = json("{\"spec\": {\"a\": 1, \"b\": 2}}");
        JsonNode overlay = json("{\"spec\": {\"b\": 3, \"c\": 4}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("spec").get("a").asInt()).isEqualTo(1);
        assertThat(result.get("spec").get("b").asInt()).isEqualTo(3);
        assertThat(result.get("spec").get("c").asInt()).isEqualTo(4);
    }

    @Test
    void nullOverlayValueRemovesKey() throws Exception {
        JsonNode base = json("{\"a\": 1, \"b\": 2}");
        JsonNode overlay = json("{\"b\": null}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("a").asInt()).isEqualTo(1);
        assertThat(result.has("b")).isFalse();
    }

    @Test
    void scalarOverlayReplacesBase() throws Exception {
        JsonNode base = json("{\"a\": \"old\"}");
        JsonNode overlay = json("{\"a\": \"new\"}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("a").asText()).isEqualTo("new");
    }

    @Test
    void namedArrayMergesByName() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\", \"val\": 1}, {\"name\": \"b\", \"val\": 2}]}");
        JsonNode overlay = json("{\"items\": [{\"name\": \"b\", \"val\": 3}]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        JsonNode items = result.get("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).get("name").asText()).isEqualTo("a");
        assertThat(items.get(0).get("val").asInt()).isEqualTo(1);
        assertThat(items.get(1).get("name").asText()).isEqualTo("b");
        assertThat(items.get(1).get("val").asInt()).isEqualTo(3);
    }

    @Test
    void namedArrayAppendsNewElements() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\", \"val\": 1}]}");
        JsonNode overlay = json("{\"items\": [{\"name\": \"b\", \"val\": 2}]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        JsonNode items = result.get("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).get("name").asText()).isEqualTo("a");
        assertThat(items.get(1).get("name").asText()).isEqualTo("b");
    }

    @Test
    void namedArrayDeepMergesMatchingElements() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\", \"nested\": {\"x\": 1, \"y\": 2}}]}");
        JsonNode overlay = json("{\"items\": [{\"name\": \"a\", \"nested\": {\"y\": 3}}]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        JsonNode nested = result.get("items").get(0).get("nested");
        assertThat(nested.get("x").asInt()).isEqualTo(1);
        assertThat(nested.get("y").asInt()).isEqualTo(3);
    }

    @Test
    void nonNamedArrayIsReplaced() throws Exception {
        JsonNode base = json("{\"tags\": [\"a\", \"b\"]}");
        JsonNode overlay = json("{\"tags\": [\"c\"]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("tags").size()).isEqualTo(1);
        assertThat(result.get("tags").get(0).asText()).isEqualTo("c");
    }

    @Test
    void emptyBaseArrayWithNamedOverlay() throws Exception {
        JsonNode base = json("{\"items\": []}");
        JsonNode overlay = json("{\"items\": [{\"name\": \"a\", \"val\": 1}]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("a");
    }

    @Test
    void customKeyFieldMerge() throws Exception {
        JsonNode base = json("{\"agents\": [{\"agentId\": \"x\", \"v\": 1}]}");
        JsonNode overlay = json("{\"agents\": [{\"agentId\": \"x\", \"v\": 2}]}");
        JsonNode result = YamlMerger.merge(base, overlay, "agentId");
        assertThat(result.get("agents").get(0).get("v").asInt()).isEqualTo(2);
    }

    @Test
    void emptyOverlayReturnsBase() throws Exception {
        JsonNode base = json("{\"a\": 1}");
        JsonNode overlay = json("{}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void nullBaseReturnsOverlay() throws Exception {
        JsonNode overlay = json("{\"a\": 1}");
        JsonNode result = YamlMerger.merge(null, overlay);
        assertThat(result.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void removeSingleElementFromNamedArray() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\", \"v\": 1}, {\"name\": \"b\", \"v\": 2}]}");
        JsonNode overlay = json("{\"remove\": {\"items\": [\"b\"]}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("a");
    }

    @Test
    void removeMultipleElementsFromSameArray() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\"}, {\"name\": \"b\"}, {\"name\": \"c\"}]}");
        JsonNode overlay = json("{\"remove\": {\"items\": [\"a\", \"c\"]}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("b");
    }

    @Test
    void removeFromMultipleArrays() throws Exception {
        JsonNode base = json("{\"bindings\": [{\"name\": \"a\"}, {\"name\": \"b\"}], \"workers\": [{\"name\": \"w1\"}, {\"name\": \"w2\"}]}");
        JsonNode overlay = json("{\"remove\": {\"bindings\": [\"a\"], \"workers\": [\"w2\"]}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("bindings").size()).isEqualTo(1);
        assertThat(result.get("bindings").get(0).get("name").asText()).isEqualTo("b");
        assertThat(result.get("workers").size()).isEqualTo(1);
        assertThat(result.get("workers").get(0).get("name").asText()).isEqualTo("w1");
    }

    @Test
    void removeAtNestedLevel() throws Exception {
        JsonNode base = json("{\"spec\": {\"bindings\": [{\"name\": \"a\"}, {\"name\": \"b\"}]}}");
        JsonNode overlay = json("{\"spec\": {\"remove\": {\"bindings\": [\"a\"]}}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("spec").get("bindings").size()).isEqualTo(1);
        assertThat(result.get("spec").get("bindings").get(0).get("name").asText()).isEqualTo("b");
        assertThat(result.get("spec").has("remove")).isFalse();
    }

    @Test
    void removeNonExistentElementIgnored() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
        JsonNode overlay = json("{\"remove\": {\"items\": [\"z\"]}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("a");
    }

    @Test
    void removeNonExistentArrayIgnored() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
        JsonNode overlay = json("{\"remove\": {\"missing\": [\"a\"]}}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
    }

    @Test
    void removeCombinedWithOverride() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\", \"v\": 1}, {\"name\": \"b\", \"v\": 2}]}");
        JsonNode overlay = json("{\"remove\": {\"items\": [\"a\"]}, \"items\": [{\"name\": \"b\", \"v\": 3}]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("b");
        assertThat(result.get("items").get(0).get("v").asInt()).isEqualTo(3);
    }

    @Test
    void removeCombinedWithAdd() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
        JsonNode overlay = json("{\"remove\": {\"items\": [\"a\"]}, \"items\": [{\"name\": \"b\"}]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("b");
    }

    @Test
    void noRemoveKeyBehaviorUnchanged() throws Exception {
        JsonNode base = json("{\"items\": [{\"name\": \"a\"}]}");
        JsonNode overlay = json("{\"items\": [{\"name\": \"b\"}]}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(2);
    }

    @Test
    void removeInBaseNotProcessed() throws Exception {
        JsonNode base = json("{\"remove\": {\"items\": [\"a\"]}, \"items\": [{\"name\": \"a\"}]}");
        JsonNode overlay = json("{}");
        JsonNode result = YamlMerger.merge(base, overlay);
        assertThat(result.get("items").size()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("name").asText()).isEqualTo("a");
        assertThat(result.has("remove")).isTrue();
    }

    @Test
    void removeWithCustomKeyField() throws Exception {
        JsonNode base = json("{\"agents\": [{\"agentId\": \"x\", \"v\": 1}, {\"agentId\": \"y\", \"v\": 2}]}");
        JsonNode overlay = json("{\"remove\": {\"agents\": [\"x\"]}}");
        JsonNode result = YamlMerger.merge(base, overlay, "agentId");
        assertThat(result.get("agents").size()).isEqualTo(1);
        assertThat(result.get("agents").get(0).get("agentId").asText()).isEqualTo("y");
    }
}
