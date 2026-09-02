package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.ActorCapacity;
import io.casehub.platform.api.capacity.ActorCapacityView;
import io.casehub.platform.api.capacity.CapacityPressureEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
public class CapacityPressureMonitor {

    private static final Logger LOG = Logger.getLogger(CapacityPressureMonitor.class);

    private final ActorCapacityView capacityView;
    private final Consumer<CapacityPressureEvent> eventSink;
    private final double sweepThreshold;

    @Inject
    public CapacityPressureMonitor(
            ActorCapacityView capacityView,
            Event<CapacityPressureEvent> pressureEvent,
            @ConfigProperty(name = "casehub.capacity.redistribution.compress-threshold",
                            defaultValue = "0.7") double sweepThreshold) {
        this(capacityView, pressureEvent::fireAsync, sweepThreshold);
    }

    CapacityPressureMonitor(ActorCapacityView capacityView,
                            Consumer<CapacityPressureEvent> eventSink,
                            double sweepThreshold) {
        this.capacityView = capacityView;
        this.eventSink = eventSink;
        this.sweepThreshold = sweepThreshold;
    }

    @Scheduled(every = "${casehub.capacity.sweep-interval:60s}",
               identity = "capacity-pressure-sweep")
    void sweep() {
        List<ActorCapacity> overloaded = capacityView.getOverloaded(sweepThreshold);

        for (var capacity : overloaded) {
            String trigger = capacity.pressureBySignalType().entrySet().stream()
                    .max(Map.Entry.<String, Double>comparingByValue()
                            .thenComparing(Map.Entry.<String, Double>comparingByKey().reversed()))
                    .map(Map.Entry::getKey)
                    .orElse("unknown");

            LOG.debugf("Actor %s overloaded: pressure=%.2f, trigger=%s",
                    capacity.actorId(), capacity.aggregatePressure(), trigger);

            eventSink.accept(new CapacityPressureEvent(
                    capacity.actorId(), capacity, sweepThreshold, trigger));
        }
    }
}
