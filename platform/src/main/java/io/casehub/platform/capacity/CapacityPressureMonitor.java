package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.CapacityPressureEvent;
import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.RedistributionAction;
import io.casehub.platform.api.capacity.RedistributionContext;
import io.casehub.platform.api.capacity.RedistributionDecision;
import io.casehub.platform.api.capacity.RedistributionPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Instant;

@ApplicationScoped
public class CapacityPressureMonitor {

    private final AggregatingActorCapacityView capacityView;
    private final RedistributionPolicy         policy;
    private final Event<CapacityPressureEvent> pressureEvent;

    @Inject
    public CapacityPressureMonitor(AggregatingActorCapacityView capacityView,
                                   RedistributionPolicy policy,
                                   Event<CapacityPressureEvent> pressureEvent) {
        this.capacityView  = capacityView;
        this.policy        = policy;
        this.pressureEvent = pressureEvent;
    }

    public void sweep() {
        capacityView.refresh();

        for (CapacitySignal aggregated : capacityView.allAggregatedPressures()) {
            RedistributionContext context = new RedistributionContext(
                    aggregated.actorId(),
                    aggregated,
                    capacityView.signalsByActor(aggregated.actorId()));

            RedistributionDecision decision = policy.evaluate(context);

            if (decision.action() != RedistributionAction.NONE) {
                pressureEvent.fireAsync(new CapacityPressureEvent(
                        aggregated.actorId(), decision, aggregated, Instant.now()));
            }
        }
    }
}
