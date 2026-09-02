package io.casehub.platform.capacity;

import io.casehub.platform.api.actor.ActorStateAccumulator;
import io.casehub.platform.api.capacity.ActorCapacity;
import io.casehub.platform.api.capacity.ActorCapacityView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityActorStateContributorTest {

    @Test
    void contributes_aggregate_pressure_and_signals() {
        var view = fixedView("agent-1", 0.85, Map.of("ctx", 0.85, "task", 0.3));
        var contributor = new CapacityActorStateContributor(view);
        var captured = new CapturingAccumulator();

        contributor.contribute("agent-1", captured);

        assertThat(captured.aggregatePressure.get()).isEqualTo(0.85);
        assertThat(captured.pressureBySignalType.get())
                .containsEntry("ctx", 0.85)
                .containsEntry("task", 0.3);
    }

    @Test
    void contributes_zero_when_no_sources() {
        var view = fixedView("agent-1", 0.0, Map.of());
        var contributor = new CapacityActorStateContributor(view);
        var captured = new CapturingAccumulator();

        contributor.contribute("agent-1", captured);

        assertThat(captured.aggregatePressure.get()).isEqualTo(0.0);
        assertThat(captured.pressureBySignalType.get()).isEmpty();
    }

    @Test
    void sourceName_is_capacity() {
        var contributor = new CapacityActorStateContributor(
                fixedView("a", 0.0, Map.of()));
        assertThat(contributor.sourceName()).isEqualTo("capacity");
    }

    private static ActorCapacityView fixedView(String actorId, double pressure,
                                                Map<String, Double> byType) {
        var cap = new ActorCapacity(actorId, pressure, byType, Instant.now());
        return new ActorCapacityView() {
            @Override public ActorCapacity getCapacity(String id) { return cap; }
            @Override public List<ActorCapacity> getOverloaded(double t) { return List.of(); }
        };
    }

    private static class CapturingAccumulator implements ActorStateAccumulator {
        final AtomicReference<Double> aggregatePressure = new AtomicReference<>();
        final AtomicReference<Map<String, Double>> pressureBySignalType = new AtomicReference<>();

        @Override public void trustScore(Double score) {}
        @Override public void capabilityScore(String capability, double score) {}
        @Override public void workItem(UUID id, String title, String status,
                                       String category, UUID caseId) {}
        @Override public void commitment(UUID commitmentId, UUID channelId,
                                         UUID caseId, String state, Instant expiresAt) {}
        @Override public void engineActiveCaseId(UUID caseId) {}

        @Override
        public void capacity(double aggregatePressure, Map<String, Double> pressureBySignalType) {
            this.aggregatePressure.set(aggregatePressure);
            this.pressureBySignalType.set(pressureBySignalType);
        }
    }
}
