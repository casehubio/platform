package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.ActorCapacity;
import io.casehub.platform.api.capacity.ActorCapacityView;
import io.casehub.platform.api.capacity.CapacityPressureEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityPressureMonitorTest {

    @Test
    void sweep_fires_event_for_each_overloaded_actor() {
        var view = stubView(List.of(
                new ActorCapacity("agent-1", 0.9, Map.of("ctx", 0.9), Instant.now()),
                new ActorCapacity("agent-2", 0.8, Map.of("task", 0.8), Instant.now())));
        var events = new ArrayList<CapacityPressureEvent>();
        var monitor = new CapacityPressureMonitor(view, events::add, 0.7);

        monitor.sweep();

        assertThat(events).hasSize(2);
        assertThat(events).extracting(CapacityPressureEvent::actorId)
                .containsExactlyInAnyOrder("agent-1", "agent-2");
    }

    @Test
    void sweep_no_overloaded_actors_fires_no_events() {
        var view = stubView(List.of());
        var events = new ArrayList<CapacityPressureEvent>();
        var monitor = new CapacityPressureMonitor(view, events::add, 0.7);

        monitor.sweep();

        assertThat(events).isEmpty();
    }

    @Test
    void sweep_identifies_highest_pressure_trigger() {
        var view = stubView(List.of(
                new ActorCapacity("agent-1", 0.9,
                        Map.of("ctx", 0.9, "task", 0.3), Instant.now())));
        var events = new ArrayList<CapacityPressureEvent>();
        var monitor = new CapacityPressureMonitor(view, events::add, 0.7);

        monitor.sweep();

        assertThat(events.get(0).triggerSignalType()).isEqualTo("ctx");
    }

    @Test
    void sweep_lexicographic_tiebreak_for_equal_pressure() {
        var view = stubView(List.of(
                new ActorCapacity("agent-1", 0.9,
                        Map.of("beta_signal", 0.9, "alpha_signal", 0.9), Instant.now())));
        var events = new ArrayList<CapacityPressureEvent>();
        var monitor = new CapacityPressureMonitor(view, events::add, 0.7);

        monitor.sweep();

        assertThat(events.get(0).triggerSignalType()).isEqualTo("alpha_signal");
    }

    @Test
    void sweep_carries_threshold_in_event() {
        var view = stubView(List.of(
                new ActorCapacity("agent-1", 0.9, Map.of("ctx", 0.9), Instant.now())));
        var events = new ArrayList<CapacityPressureEvent>();
        var monitor = new CapacityPressureMonitor(view, events::add, 0.7);

        monitor.sweep();

        assertThat(events.get(0).threshold()).isEqualTo(0.7);
    }

    @Test
    void sweep_empty_pressure_map_uses_unknown_trigger() {
        var view = stubView(List.of(
                new ActorCapacity("agent-1", 0.9, Map.of(), Instant.now())));
        var events = new ArrayList<CapacityPressureEvent>();
        var monitor = new CapacityPressureMonitor(view, events::add, 0.7);

        monitor.sweep();

        assertThat(events.get(0).triggerSignalType()).isEqualTo("unknown");
    }

    private static ActorCapacityView stubView(List<ActorCapacity> overloaded) {
        return new ActorCapacityView() {
            @Override
            public ActorCapacity getCapacity(String actorId) {
                return overloaded.stream()
                        .filter(c -> c.actorId().equals(actorId))
                        .findFirst()
                        .orElse(new ActorCapacity(actorId, 0.0, Map.of(), Instant.now()));
            }

            @Override
            public List<ActorCapacity> getOverloaded(double threshold) {
                return overloaded;
            }
        };
    }
}
