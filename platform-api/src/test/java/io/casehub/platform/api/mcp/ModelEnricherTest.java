package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelEnricherTest {

    @McpDomain("test")
    static class TestEnricher implements ModelEnricher {
        @Override
        public String summary() { return "Test domain"; }

        @Override
        public Map<String, Object> state() { return Map.of("count", 42); }
    }

    @McpDomain("minimal")
    static class MinimalEnricher implements ModelEnricher {}

    @Test
    void enricherWithOverrides() {
        var enricher = new TestEnricher();
        assertThat(enricher.summary()).isEqualTo("Test domain");
        assertThat(enricher.state()).containsEntry("count", 42);

        McpDomain domain = TestEnricher.class.getAnnotation(McpDomain.class);
        assertThat(domain.value()).isEqualTo("test");
    }

    @Test
    void enricherDefaults() {
        var enricher = new MinimalEnricher();
        assertThat(enricher.summary()).isEmpty();
        assertThat(enricher.state()).isEmpty();
    }
}
