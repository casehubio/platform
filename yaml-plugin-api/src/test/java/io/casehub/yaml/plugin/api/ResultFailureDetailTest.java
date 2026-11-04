package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFailureDetailTest {

    @Test
    void failedWithDetail_carriesDetail() {
        var result = Result.failed("boom", "timeout", Map.of("timeout", "PT30S"));
        assertThat(result).isInstanceOf(Result.Failure.class);
        var f = (Result.Failure) result;
        assertThat(f.detail()).containsEntry("timeout", "PT30S");
        assertThat(f.message()).isEqualTo("boom");
        assertThat(f.category()).isEqualTo("timeout");
    }

    @Test
    void failedWithoutDetail_emptyMap() {
        var f = (Result.Failure) Result.failed("boom", "timeout");
        assertThat(f.detail()).isEmpty();
    }

    @Test
    void failedMessageOnly_emptyDetail() {
        var f = (Result.Failure) Result.failed("boom");
        assertThat(f.detail()).isEmpty();
        assertThat(f.category()).isNull();
    }

    @Test
    void detail_isImmutable() {
        var mutable = new java.util.HashMap<String, Object>();
        mutable.put("k", "v");
        var f = (Result.Failure) Result.failed("boom", "cat", mutable);
        mutable.put("k2", "v2");
        assertThat(f.detail()).doesNotContainKey("k2");
    }

    @Test
    void failureCategory_usesKebabCase() {
        assertThat(FailureCategory.TIMEOUT).isEqualTo("timeout");
        assertThat(FailureCategory.TRANSIENT).isEqualTo("transient");
        assertThat(FailureCategory.PERMANENT).isEqualTo("permanent");
        assertThat(FailureCategory.RATE_LIMITED).isEqualTo("rate-limit");
    }
}
