package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class McpResourceReadRequestTest {

    @Test
    void factoryCreatesEmptyTemplateArgs() {
        var req = McpResourceReadRequest.of("casehub://index");
        assertThat(req.uri()).isEqualTo("casehub://index");
        assertThat(req.templateArgs()).isEmpty();
    }

    @Test
    void constructorDefensivelyCopiesArgs() {
        var args = new HashMap<>(Map.of("k", "v"));
        var req = new McpResourceReadRequest("uri", args);
        args.put("extra", "val");
        assertThat(req.templateArgs()).doesNotContainKey("extra");
    }

    @Test
    void nullArgsBecomeEmptyMap() {
        var req = new McpResourceReadRequest("uri", null);
        assertThat(req.templateArgs()).isEmpty();
    }

    @Test
    void nullUriThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> McpResourceReadRequest.of(null));
    }
}
