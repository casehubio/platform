package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.ActorCapacity;
import io.casehub.platform.api.capacity.ActorCapacityView;
import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.CapacitySignalSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AggregatingActorCapacityView implements ActorCapacityView {

    private final Iterable<CapacitySignalSource> signalSources;

    @Inject
    public AggregatingActorCapacityView(@Any Instance<CapacitySignalSource> signalSources) {
        this.signalSources = signalSources;
    }

    AggregatingActorCapacityView(List<CapacitySignalSource> signalSources) {
        this.signalSources = signalSources;
    }

    @Override
    public ActorCapacity getCapacity(String actorId) {
        Map<String, Double> pressureByType = new HashMap<>();
        for (CapacitySignalSource source : signalSources) {
            try {
                for (CapacitySignal signal : source.observe(actorId)) {
                    pressureByType.merge(signal.signalType(), signal.pressure(), Math::max);
                }
            } catch (Exception e) {
                // error-isolated per source
            }
        }
        double maxPressure = pressureByType.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0.0);
        return new ActorCapacity(actorId, maxPressure, Map.copyOf(pressureByType), Instant.now());
    }

    @Override
    public List<ActorCapacity> getOverloaded(double threshold) {
        Map<String, Map<String, Double>> byActor = new HashMap<>();
        for (CapacitySignalSource source : signalSources) {
            try {
                for (CapacitySignal signal : source.observeOverloaded(threshold)) {
                    byActor.computeIfAbsent(signal.actorId(), k -> new HashMap<>())
                            .merge(signal.signalType(), signal.pressure(), Math::max);
                }
            } catch (Exception e) {
                // error-isolated per source
            }
        }
        return byActor.entrySet().stream()
                .map(e -> {
                    double max = e.getValue().values().stream()
                            .mapToDouble(Double::doubleValue).max().orElse(0.0);
                    return new ActorCapacity(e.getKey(), max, Map.copyOf(e.getValue()), Instant.now());
                })
                .filter(c -> c.aggregatePressure() >= threshold)
                .toList();
    }
}
