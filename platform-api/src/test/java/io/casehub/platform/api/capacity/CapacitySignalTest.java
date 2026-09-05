package io.casehub.platform.api.capacity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapacitySignalTest {

    @Test
    void valid_signal() {
        var signal = new CapacitySignal("actor-1", "work-queue", 0.75, Instant.now());
        assertThat(signal.actorId()).isEqualTo("actor-1");
        assertThat(signal.signalType()).isEqualTo("work-queue");
        assertThat(signal.pressure()).isEqualTo(0.75);
        assertThat(signal.metadata()).isEmpty();
    }

    @Test
    void pressure_below_zero_throws() {
        assertThatThrownBy(() -> new CapacitySignal("a", "s", -0.1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0");
    }

    @Test
    void pressure_above_one_throws() {
        assertThatThrownBy(() -> new CapacitySignal("a", "s", 1.1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1.0");
    }

    @Test
    void pressure_boundary_zero_accepted() {
        var signal = new CapacitySignal("a", "s", 0.0, Instant.now());
        assertThat(signal.pressure()).isEqualTo(0.0);
    }

    @Test
    void pressure_boundary_one_accepted() {
        var signal = new CapacitySignal("a", "s", 1.0, Instant.now());
        assertThat(signal.pressure()).isEqualTo(1.0);
    }

    @Test
    void null_actorId_throws() {
        assertThatThrownBy(() -> new CapacitySignal(null, "s", 0.5, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_signalType_throws() {
        assertThatThrownBy(() -> new CapacitySignal("a", null, 0.5, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_observedAt_defaults_to_now() {
        var before = Instant.now();
        var signal = new CapacitySignal("a", "s", 0.5, null);
        assertThat(signal.observedAt()).isAfterOrEqualTo(before);
    }
}
