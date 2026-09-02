package io.casehub.platform.capacity;

import io.casehub.platform.api.actor.ActorStateAccumulator;
import io.casehub.platform.api.actor.ActorStateContributor;
import io.casehub.platform.api.capacity.ActorCapacity;
import io.casehub.platform.api.capacity.ActorCapacityView;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CapacityActorStateContributor implements ActorStateContributor {

    private final ActorCapacityView capacityView;

    @Inject
    public CapacityActorStateContributor(ActorCapacityView capacityView) {
        this.capacityView = capacityView;
    }

    @Override
    public String sourceName() {
        return "capacity";
    }

    @Override
    public void contribute(String actorId, ActorStateAccumulator accumulator) {
        ActorCapacity cap = capacityView.getCapacity(actorId);
        accumulator.capacity(cap.aggregatePressure(), cap.pressureBySignalType());
    }
}
