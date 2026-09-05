package io.casehub.platform.api.capacity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorCapacityTest {

    @Test
    void valid_capacity() {
        var cap = new ActorCapacity("actor-1", 0.75, Map.of("ctx", 0.75), Instant.now());
        assertThat(cap.actorId()).isEqualTo("actor-1");
        assertThat(cap.aggregatePressure()).isEqualTo(0.75);
        assertThat(cap.pressureBySignalType()).containsEntry("ctx", 0.75);
    }

    @Test
    void pressure_below_zero_throws() {
        assertThatThrownBy(() -> new ActorCapacity("a", -0.1, Map.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pressure_above_one_throws() {
        assertThatThrownBy(() -> new ActorCapacity("a", 1.1, Map.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_pressureBySignalType_defaults_to_empty() {
        var cap = new ActorCapacity("a", 0.5, null, Instant.now());
        assertThat(cap.pressureBySignalType()).isEmpty();
    }

    @Test
    void null_observedAt_defaults_to_now() {
        var before = Instant.now();
        var cap = new ActorCapacity("a", 0.5, Map.of(), null);
        assertThat(cap.observedAt()).isAfterOrEqualTo(before);
    }
}
