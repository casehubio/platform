package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.CapacityPressureEvent;
import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.CapacitySignalSource;
import io.casehub.platform.api.capacity.RedistributionAction;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CapacityPressureMonitorTest {

    private final List<CapacityPressureEvent> firedEvents = new ArrayList<>();

    private final Event<CapacityPressureEvent> captureEvent = new Event<>() {
        @Override public void fire(CapacityPressureEvent event) { firedEvents.add(event); }
        @Override public <U extends CapacityPressureEvent> java.util.concurrent.CompletionStage<U> fireAsync(U event) {
            firedEvents.add(event);
            return java.util.concurrent.CompletableFuture.completedFuture(event);
        }
        @Override public <U extends CapacityPressureEvent> java.util.concurrent.CompletionStage<U> fireAsync(U event, jakarta.enterprise.event.NotificationOptions options) {
            return fireAsync(event);
        }
        @Override public Event<CapacityPressureEvent> select(java.lang.annotation.Annotation... qualifiers) { return this; }
        @Override public <U extends CapacityPressureEvent> Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends CapacityPressureEvent> Event<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    };

    private CapacityPressureMonitor monitor(List<CapacitySignalSource> sources) {
        var view = new AggregatingActorCapacityView(sources);
        var policy = new DefaultRedistributionPolicy(0.7, 0.85, 0.95);
        return new CapacityPressureMonitor(view, policy, captureEvent);
    }

    @Test
    void fires_event_on_pressure() {
        CapacitySignalSource source = new CapacitySignalSource() {
            @Override public String sourceName() { return "s"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("actor-1", "s", 0.9, Instant.now()));
            }
        };
        var mon = monitor(List.of(source));
        mon.sweep();

        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.get(0).actorId()).isEqualTo("actor-1");
        assertThat(firedEvents.get(0).decision().action())
                .isEqualTo(RedistributionAction.REDISTRIBUTE);
    }

    @Test
    void no_event_below_threshold() {
        CapacitySignalSource source = new CapacitySignalSource() {
            @Override public String sourceName() { return "s"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("actor-1", "s", 0.3, Instant.now()));
            }
        };
        var mon = monitor(List.of(source));
        mon.sweep();

        assertThat(firedEvents).isEmpty();
    }

    @Test
    void multiple_actors_each_evaluated() {
        CapacitySignalSource source = new CapacitySignalSource() {
            @Override public String sourceName() { return "s"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(
                        new CapacitySignal("low", "s", 0.3, Instant.now()),
                        new CapacitySignal("high", "s", 0.97, Instant.now()));
            }
        };
        var mon = monitor(List.of(source));
        mon.sweep();

        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.get(0).actorId()).isEqualTo("high");
        assertThat(firedEvents.get(0).decision().action())
                .isEqualTo(RedistributionAction.ESCALATE);
    }
}
