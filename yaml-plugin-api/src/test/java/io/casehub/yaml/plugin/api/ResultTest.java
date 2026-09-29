package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultTest {

    @Test
    void successResult() {
        Result result = Result.of(Map.of("exitCode", 0, "stdout", "ok"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("exitCode", 0);
        assertThat(result.output()).containsEntry("stdout", "ok");
    }

    @Test
    void failureResult() {
        Result result = Result.failed("connection refused");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.output()).isEmpty();
        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure) result).message()).isEqualTo("connection refused");
    }

    @Test
    void successWithEmptyOutput() {
        Result result = Result.of(Map.of());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEmpty();
    }


    @Test
    void successWithMetadata() {
        var result = Result.of(Map.of("key", "value"), Map.of("cost", 0.05));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("key", "value");
        assertThat(result.executionMetadata()).containsEntry("cost", 0.05);
    }

    @Test
    void successWithoutMetadataDefaultsToEmpty() {
        var result = Result.of(Map.of("key", "value"));
        assertThat(result.executionMetadata()).isEmpty();
    }

    @Test
    void failureMetadataIsEmpty() {
        var result = Result.failed("error");
        assertThat(result.executionMetadata()).isEmpty();
    }

    @Test
    void outputIsImmutable() {
        var mutable = new HashMap<String, Object>();
        mutable.put("key", "value");
        Result result = Result.of(mutable);
        assertThrows(UnsupportedOperationException.class,
            () -> result.output().put("another", "value"));
    }
}
