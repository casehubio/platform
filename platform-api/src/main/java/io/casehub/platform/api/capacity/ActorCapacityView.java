package io.casehub.platform.api.capacity;

import java.util.List;

public interface ActorCapacityView {

    CapacitySignal aggregatedPressure(String actorId);

    List<CapacitySignal> signalsByActor(String actorId);

    List<CapacitySignal> allAggregatedPressures();
}
