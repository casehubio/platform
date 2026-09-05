package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.ActorCapacity;
import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.CapacitySignalSource;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AggregatingActorCapacityViewTest {

    @Test
    void no_sources_returns_zero_pressure() {
        var view = new AggregatingActorCapacityView(List.of());
        var result = view.getCapacity("actor-1");
        assertThat(result.aggregatePressure()).isEqualTo(0.0);
    }

    @Test
    void single_source_returns_its_pressure() {
        var source = stubSource(List.of(
                new CapacitySignal("actor-1", "work-queue", 0.6, Instant.now())));
        var view = new AggregatingActorCapacityView(List.of(source));
        assertThat(view.getCapacity("actor-1").aggregatePressure()).isEqualTo(0.6);
    }

    @Test
    void max_pressure_across_sources() {
        var s1 = stubSource(List.of(new CapacitySignal("actor-1", "s1", 0.4, Instant.now())));
        var s2 = stubSource(List.of(new CapacitySignal("actor-1", "s2", 0.8, Instant.now())));
        var view = new AggregatingActorCapacityView(List.of(s1, s2));
        assertThat(view.getCapacity("actor-1").aggregatePressure()).isEqualTo(0.8);
    }

    @Test
    void pressure_by_signal_type() {
        var s1 = stubSource(List.of(new CapacitySignal("actor-1", "ctx", 0.4, Instant.now())));
        var s2 = stubSource(List.of(new CapacitySignal("actor-1", "task", 0.8, Instant.now())));
        var view = new AggregatingActorCapacityView(List.of(s1, s2));
        var cap = view.getCapacity("actor-1");
        assertThat(cap.pressureBySignalType()).containsEntry("ctx", 0.4);
        assertThat(cap.pressureBySignalType()).containsEntry("task", 0.8);
    }

    @Test
    void get_overloaded_filters_by_threshold() {
        var source = overloadedSource(List.of(
                new CapacitySignal("a", "s", 0.3, Instant.now()),
                new CapacitySignal("b", "s", 0.9, Instant.now())));
        var view = new AggregatingActorCapacityView(List.of(source));
        var overloaded = view.getOverloaded(0.7);
        assertThat(overloaded).hasSize(1);
        assertThat(overloaded.get(0).actorId()).isEqualTo("b");
    }

    @Test
    void error_isolated_per_source() {
        var good = stubSource(List.of(new CapacitySignal("actor-1", "s", 0.5, Instant.now())));
        CapacitySignalSource bad = new CapacitySignalSource() {
            @Override public List<CapacitySignal> observe(String actorId) { throw new RuntimeException("fail"); }
            @Override public List<CapacitySignal> observeOverloaded(double t) { throw new RuntimeException("fail"); }
        };
        var view = new AggregatingActorCapacityView(List.of(bad, good));
        assertThat(view.getCapacity("actor-1").aggregatePressure()).isEqualTo(0.5);
    }

    private static CapacitySignalSource stubSource(List<CapacitySignal> signals) {
        return new CapacitySignalSource() {
            @Override public List<CapacitySignal> observe(String actorId) {
                return signals.stream().filter(s -> s.actorId().equals(actorId)).toList();
            }
            @Override public List<CapacitySignal> observeOverloaded(double threshold) {
                return signals.stream().filter(s -> s.pressure() >= threshold).toList();
            }
        };
    }

    private static CapacitySignalSource overloadedSource(List<CapacitySignal> signals) {
        return new CapacitySignalSource() {
            @Override public List<CapacitySignal> observe(String actorId) {
                return signals.stream().filter(s -> s.actorId().equals(actorId)).toList();
            }
            @Override public List<CapacitySignal> observeOverloaded(double threshold) {
                return signals;
            }
        };
    }
}
