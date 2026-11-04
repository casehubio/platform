package io.casehub.yaml.core.error;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewRuntimeStepErrorTest {

    private static final SourceLocation LOC = new SourceLocation("test.yaml", 1, -1);

    @Test
    void rateLimitError_category_and_detail() {
        var err = new RuntimeStepError.RateLimitError("s", 10.0, 5000L, LOC, null);
        assertThat(err.category()).isEqualTo(ErrorCategory.RATE_LIMIT);
        assertThat(err.toDetailMap())
                .containsEntry("permitsPerSecond", 10.0)
                .containsEntry("retryAfterMs", 5000L);
    }

    @Test
    void sessionLimitError_category_and_detail() {
        var err = new RuntimeStepError.SessionLimitError("s", 5, LOC, null);
        assertThat(err.category()).isEqualTo(ErrorCategory.SESSION_LIMIT);
        assertThat(err.toDetailMap()).containsEntry("limit", 5);
    }

    @Test
    void infrastructureError_category_and_detail() {
        var err = new RuntimeStepError.InfrastructureError("s", "agent-process", "connection refused", LOC, null);
        assertThat(err.category()).isEqualTo(ErrorCategory.INFRASTRUCTURE);
        assertThat(err.toDetailMap())
                .containsEntry("component", "agent-process")
                .containsEntry("rootCause", "connection refused");
    }

    @Test
    void authDeniedError_category_and_detail() {
        var err = new RuntimeStepError.AuthDeniedError("s", "user-1", "CASE:123", "read", LOC, null);
        assertThat(err.category()).isEqualTo(ErrorCategory.AUTH_DENIED);
        assertThat(err.toDetailMap())
                .containsEntry("actorId", "user-1")
                .containsEntry("resource", "CASE:123")
                .containsEntry("action", "read");
    }

    @Test
    void categorizedStepActionError_withDetail() {
        var detail = Map.<String, Object>of("retryAfterMs", 5000L);
        var err = new RuntimeStepError.CategorizedStepActionError(
                "s", null, "rate limited", ErrorCategory.RATE_LIMIT, null, detail, LOC, null);
        assertThat(err.category()).isEqualTo(ErrorCategory.RATE_LIMIT);
        assertThat(err.toDetailMap()).containsEntry("retryAfterMs", 5000L);
    }

    @Test
    void categorizedStepActionError_withOriginalCategory() {
        var err = new RuntimeStepError.CategorizedStepActionError(
                "s", null, "transient", ErrorCategory.STEP_ACTION_FAILED, "transient", Map.of(), LOC, null);
        assertThat(err.toDetailMap()).containsEntry("originalCategory", "transient");
    }

    @Test
    void categorizedStepActionError_detailIsDefensivelyCopied() {
        var mutable = new java.util.HashMap<String, Object>();
        mutable.put("k", "v");
        var err = new RuntimeStepError.CategorizedStepActionError(
                "s", null, "msg", ErrorCategory.RATE_LIMIT, null, mutable, LOC, null);
        mutable.put("k2", "v2");
        assertThat(err.toDetailMap()).doesNotContainKey("k2");
    }
}
