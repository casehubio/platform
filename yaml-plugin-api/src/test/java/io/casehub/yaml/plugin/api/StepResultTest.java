package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StepResultTest {

    @Test
    void successResult() {
        StepResult result = StepResult.of(Map.of("exitCode", 0, "stdout", "ok"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("exitCode", 0);
        assertThat(result.output()).containsEntry("stdout", "ok");
    }

    @Test
    void failureResult() {
        StepResult result = StepResult.failed("connection refused");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.output()).isEmpty();
        assertThat(result).isInstanceOf(StepResult.Failure.class);
        assertThat(((StepResult.Failure) result).message()).isEqualTo("connection refused");
    }

    @Test
    void successWithEmptyOutput() {
        StepResult result = StepResult.of(Map.of());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEmpty();
    }


    @Test
    void successWithMetadata() {
        var result = StepResult.of(Map.of("key", "value"), Map.of("cost", 0.05));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("key", "value");
        assertThat(result.executionMetadata()).containsEntry("cost", 0.05);
    }

    @Test
    void successWithoutMetadataDefaultsToEmpty() {
        var result = StepResult.of(Map.of("key", "value"));
        assertThat(result.executionMetadata()).isEmpty();
    }

    @Test
    void failureMetadataIsEmpty() {
        var result = StepResult.failed("error");
        assertThat(result.executionMetadata()).isEmpty();
    }

    @Test
    void outputIsImmutable() {
        var mutable = new HashMap<String, Object>();
        mutable.put("key", "value");
        StepResult result = StepResult.of(mutable);
        assertThrows(UnsupportedOperationException.class,
            () -> result.output().put("another", "value"));
    }
}
