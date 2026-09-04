package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.ActorCapacityView;
import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.CapacitySignalSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class AggregatingActorCapacityView implements ActorCapacityView {

    private final    Iterable<CapacitySignalSource>    signalSources;
    private volatile Map<String, List<CapacitySignal>> signalCache = Map.of();

    @Inject
    public AggregatingActorCapacityView(@Any Instance<CapacitySignalSource> signalSources) {
        this.signalSources = signalSources;
    }

    AggregatingActorCapacityView(List<CapacitySignalSource> signalSources) {
        this.signalSources = signalSources;
    }

    public void refresh() {
        Map<String, List<CapacitySignal>> fresh = new ConcurrentHashMap<>();
        for (CapacitySignalSource source : signalSources) {
            for (CapacitySignal signal : source.signals()) {
                fresh.computeIfAbsent(signal.actorId(), k -> new CopyOnWriteArrayList<>())
                     .add(signal);
            }
        }
        signalCache = Map.copyOf(fresh.entrySet().stream()
                                      .collect(java.util.stream.Collectors.toMap(
                                              Map.Entry::getKey, e -> List.copyOf(e.getValue()))));
    }

    @Override
    public CapacitySignal aggregatedPressure(String actorId) {
        List<CapacitySignal> signals = signalCache.getOrDefault(actorId, List.of());
        if (signals.isEmpty()) {
            return new CapacitySignal(actorId, "aggregated", 0.0, Instant.now());
        }
        double maxPressure = signals.stream()
                                    .mapToDouble(CapacitySignal::pressure)
                                    .max().orElse(0.0);
        return new CapacitySignal(actorId, "aggregated", maxPressure, Instant.now());
    }

    @Override
    public List<CapacitySignal> signalsByActor(String actorId) {
        return signalCache.getOrDefault(actorId, List.of());
    }

    @Override
    public List<CapacitySignal> allAggregatedPressures() {
        return signalCache.keySet().stream()
                          .map(this::aggregatedPressure)
                          .toList();
    }
}
