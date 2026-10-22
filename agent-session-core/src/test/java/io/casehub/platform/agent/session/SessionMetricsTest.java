package io.casehub.platform.agent.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMetricsTest {

    @Test
    void freshMetricsAreAllZero() {
        var metrics = new SessionMetrics("test-pool");

        assertThat(metrics.sessionsOpened()).isZero();
        assertThat(metrics.sessionsClosed()).isZero();
        assertThat(metrics.peakConcurrent()).isZero();
        assertThat(metrics.currentActive()).isZero();
        assertThat(metrics.totalQueries()).isZero();
        assertThat(metrics.totalClears()).isZero();
    }

    @Test
    void sessionOpenedIncrementsCountAndTracksConcurrent() {
        var metrics = new SessionMetrics("test-pool");

        metrics.sessionOpened();
        assertThat(metrics.sessionsOpened()).isEqualTo(1);
        assertThat(metrics.currentActive()).isEqualTo(1);
        assertThat(metrics.peakConcurrent()).isEqualTo(1);

        metrics.sessionOpened();
        assertThat(metrics.sessionsOpened()).isEqualTo(2);
        assertThat(metrics.currentActive()).isEqualTo(2);
        assertThat(metrics.peakConcurrent()).isEqualTo(2);
    }

    @Test
    void sessionClosedDecrementsActive() {
        var metrics = new SessionMetrics("test-pool");

        metrics.sessionOpened();
        metrics.sessionOpened();
        metrics.sessionClosed();

        assertThat(metrics.currentActive()).isEqualTo(1);
        assertThat(metrics.sessionsClosed()).isEqualTo(1);
        assertThat(metrics.peakConcurrent()).isEqualTo(2);
    }

    @Test
    void peakConcurrentTracksHighWaterMark() {
        var metrics = new SessionMetrics("test-pool");

        metrics.sessionOpened();
        metrics.sessionOpened();
        metrics.sessionOpened();
        assertThat(metrics.peakConcurrent()).isEqualTo(3);

        metrics.sessionClosed();
        metrics.sessionClosed();
        assertThat(metrics.peakConcurrent()).isEqualTo(3);

        metrics.sessionOpened();
        assertThat(metrics.peakConcurrent()).isEqualTo(3);
    }

    @Test
    void queryStartedIncrements() {
        var metrics = new SessionMetrics("test-pool");

        metrics.queryStarted();
        metrics.queryStarted();
        metrics.queryStarted();

        assertThat(metrics.totalQueries()).isEqualTo(3);
    }

    @Test
    void clearTriggeredIncrements() {
        var metrics = new SessionMetrics("test-pool");

        metrics.clearTriggered();
        metrics.clearTriggered();

        assertThat(metrics.totalClears()).isEqualTo(2);
    }

    @Test
    void summaryContainsAllFields() {
        var metrics = new SessionMetrics("test-pool");

        metrics.sessionOpened();
        metrics.queryStarted();
        metrics.queryStarted();
        metrics.clearTriggered();

        String summary = metrics.summary();

        assertThat(summary).contains("sessions=1");
        assertThat(summary).contains("peakConcurrent=1");
        assertThat(summary).contains("queries=2");
        assertThat(summary).contains("clears=1");
    }

    @Test
    void nameIsAccessible() {
        var metrics = new SessionMetrics("character-pool");

        assertThat(metrics.name()).isEqualTo("character-pool");
    }
}
