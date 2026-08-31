package io.casehub.ts.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TsjTsExecutorTest {

    private final TsExecutor executor = new TsjTsExecutor();

    @Test
    void evaluateValidTsReturnsJson() throws IOException {
        Path tmpFile = Files.createTempFile("tsj-test-", ".ts");
        try {
            Files.writeString(tmpFile,
                    "const data = { kind: \"single\", namespace: \"test\" };\n"
                    + "console.log(JSON.stringify(data));");

            var result = executor.evaluate(tmpFile);

            assertThat(result.success())
                    .as("Expected success but got errors: %s", result.errors())
                    .isTrue();
            assertThat(result.json()).contains("\"kind\":\"single\"");
            assertThat(result.json()).contains("\"namespace\":\"test\"");
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    @Test
    void evaluateFromStringReturnsJson() {
        var result = executor.evaluate(
                "console.log(JSON.stringify({ value: \"hello\" }));");

        assertThat(result.success())
                .as("Expected success but got errors: %s", result.errors())
                .isTrue();
        assertThat(result.json()).contains("\"value\":\"hello\"");
    }

    @Test
    void evaluateSyntaxErrorReturnsTsError() throws IOException {
        Path tmpFile = Files.createTempFile("tsj-test-", ".ts");
        try {
            Files.writeString(tmpFile, "const x: = ;");
            var result = executor.evaluate(tmpFile);

            assertThat(result.success()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    @Test
    void evaluateRuntimeErrorReturnsTsError() throws IOException {
        Path tmpFile = Files.createTempFile("tsj-test-", ".ts");
        try {
            Files.writeString(tmpFile, "throw new Error(\"boom\");");
            var result = executor.evaluate(tmpFile);

            assertThat(result.success()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }
}
