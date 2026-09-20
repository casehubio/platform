package io.casehub.platform.simulation.event.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class TemporalDriverServiceTest {

    @Inject
    TemporalDriverService service;

    @AfterEach
    void stopAll() {
        for (var status : service.list()) {
            try {
                service.stop(status.name());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void startInlineAndStop() throws InterruptedException {
        var request = new TemporalDriverStartRequest(
                "test-inline", null, "test.event", "tenant-1",
                List.of(new TemporalEventInput("0", "evt-1",
                        Map.of("key", "value"))),
                false, 1.0);

        var status = service.start(request);
        assertThat(status.name()).isEqualTo("test-inline");
        assertThat(status.state()).isIn("RUNNING", "COMPLETED");

        Thread.sleep(100);
        service.stop("test-inline");

        assertThat(service.list()).isEmpty();
    }

    @Test
    void startWithSpeedOverride() throws InterruptedException {
        var request = new TemporalDriverStartRequest(
                "speed-test", null, "test.event", null,
                List.of(new TemporalEventInput("10ms", "step-1",
                        Map.of("device", "sensor-1"))),
                false, 10.0);

        var status = service.start(request);
        assertThat(status.name()).isEqualTo("speed-test");
        assertThat(status.speed()).isEqualTo(10.0);

        Thread.sleep(100);
        service.stop("speed-test");
    }

    @Test
    void conflictOnDuplicateStart() {
        var request = new TemporalDriverStartRequest(
                "conflict-test", null, "test.event", null,
                List.of(new TemporalEventInput("1s", "slow",
                        Map.of("k", "v"))),
                true, 1.0);

        service.start(request);

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(IllegalStateException.class);

        service.stop("conflict-test");
    }

    @Test
    void completedDriverAutoReplacedOnStart() throws InterruptedException {
        var request = new TemporalDriverStartRequest(
                "replace-test", null, "test.event", null,
                List.of(new TemporalEventInput("0", "fast",
                        Map.of("k", "v"))),
                false, 100.0);

        service.start(request);
        Thread.sleep(200);

        var status = service.status("replace-test");
        assertThat(status.state()).isEqualTo("COMPLETED");

        var status2 = service.start(request);
        assertThat(status2.name()).isEqualTo("replace-test");

        Thread.sleep(200);
        service.stop("replace-test");
    }

    @Test
    void pauseAndResume() throws InterruptedException {
        var request = new TemporalDriverStartRequest(
                "pause-test", null, "test.event", null,
                List.of(
                        new TemporalEventInput("50ms", "a", Map.of("k", "1")),
                        new TemporalEventInput("50ms", "b", Map.of("k", "2")),
                        new TemporalEventInput("50ms", "c", Map.of("k", "3"))),
                true, 1.0);

        service.start(request);
        Thread.sleep(30);

        service.pause("pause-test");
        var paused = service.status("pause-test");
        assertThat(paused.state()).isEqualTo("PAUSED");

        service.resume("pause-test");
        var resumed = service.status("pause-test");
        assertThat(resumed.state()).isEqualTo("RUNNING");

        service.stop("pause-test");
    }

    @Test
    void setSpeedMidFlight() throws InterruptedException {
        var request = new TemporalDriverStartRequest(
                "speed-change", null, "test.event", null,
                List.of(new TemporalEventInput("1s", "slow",
                        Map.of("k", "v"))),
                true, 1.0);

        service.start(request);
        var before = service.status("speed-change");
        assertThat(before.speed()).isEqualTo(1.0);

        service.setSpeed(new TemporalDriverSpeedRequest("speed-change", 50.0));
        var after = service.status("speed-change");
        assertThat(after.speed()).isEqualTo(50.0);

        service.stop("speed-change");
    }

    @Test
    void statusNotFound() {
        assertThatThrownBy(() -> service.status("nonexistent"))
                .isInstanceOf(jakarta.ws.rs.NotFoundException.class);
    }

    @Test
    void listReturnsAllActive() {
        service.start(new TemporalDriverStartRequest(
                "list-a", null, "test.a", null,
                List.of(new TemporalEventInput("1s", "a", Map.of())),
                true, 1.0));
        service.start(new TemporalDriverStartRequest(
                "list-b", null, "test.b", null,
                List.of(new TemporalEventInput("1s", "b", Map.of())),
                true, 1.0));

        var list = service.list();
        assertThat(list).hasSize(2);
        assertThat(list).extracting(TemporalDriverStatus::name)
                .containsExactlyInAnyOrder("list-a", "list-b");

        service.stop("list-a");
        service.stop("list-b");
    }

    @Test
    void validationRejectsNeitherProfileNorQualified() {
        var bad = new TemporalDriverStartRequest(
                "bad", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.start(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validationRejectsBothProfileAndQualified() {
        var bad = new TemporalDriverStartRequest(
                "bad", "profile", "qualified", null, null, null, null);
        assertThatThrownBy(() -> service.start(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameDefaultsToProfileName() {
        var request = new TemporalDriverStartRequest(
                null, null, "test.default", null,
                List.of(new TemporalEventInput("0", "a", Map.of())),
                false, 1.0);

        assertThatThrownBy(() -> request.effectiveName())
                .isInstanceOf(IllegalArgumentException.class);

        var withProfile = new TemporalDriverStartRequest(
                null, "my-profile", null, null, null, null, null);
        assertThat(withProfile.effectiveName()).isEqualTo("my-profile");
    }
}
