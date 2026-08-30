package io.casehub.ts.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NodeTsExecutorTest {

    private static final Path TEST_RESOURCES = Path.of("src/test/resources");
    private final TsExecutor executor = new NodeTsExecutor();

    @BeforeAll
    static void checkNodeAvailable() {
        try {
            var pb = new ProcessBuilder("node", "--version");
            var p = pb.start();
            int exit = p.waitFor();
            org.junit.jupiter.api.Assumptions.assumeTrue(exit == 0,
                    "Node.js not available — skipping NodeTsExecutor tests");
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Node.js not available — skipping NodeTsExecutor tests");
        }
    }

    @Test
    void evaluateValidTsReturnsJson() {
        var result = executor.evaluate(TEST_RESOURCES.resolve("valid-graph.ts"));

        assertThat(result.success()).isTrue();
        assertThat(result.json()).contains("\"kind\":\"single\"");
        assertThat(result.json()).contains("\"namespace\":\"test\"");
        assertThat(result.json()).contains("\"name\":\"simple\"");
    }

    @Test
    void evaluateValidTsFromStringReturnsJson() {
        var result = executor.evaluate("""
                export default {
                    kind: 'single',
                    namespace: 'inline',
                    name: 'test',
                    nodes: [],
                    dependencies: []
                };
                """);

        assertThat(result.success()).isTrue();
        assertThat(result.json()).contains("\"namespace\":\"inline\"");
    }

    @Test
    void evaluateSyntaxErrorReturnsTsError() {
        var result = executor.evaluate(TEST_RESOURCES.resolve("invalid-syntax.ts"));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().get(0).message()).isNotBlank();
    }

    @Test
    void evaluateRuntimeErrorReturnsTsError() {
        var result = executor.evaluate(TEST_RESOURCES.resolve("runtime-error.ts"));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().get(0).message()).contains("deliberate runtime error");
    }
}
