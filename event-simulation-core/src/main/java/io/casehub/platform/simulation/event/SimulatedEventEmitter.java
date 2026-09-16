package io.casehub.platform.simulation.event;

import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.SimulationStrategy;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class SimulatedEventEmitter {

    private final SimulationRuntime simulation;
    private final Consumer<CloudEvent> eventSink;
    private final List<EventSourceConfig> sources;

    public SimulatedEventEmitter(final SimulationRuntime simulation,
                                  final Consumer<CloudEvent> eventSink,
                                  final List<EventSourceConfig> sources) {
        this.simulation = simulation;
        this.eventSink = eventSink;
        this.sources = List.copyOf(sources);
    }

    @SuppressWarnings("unchecked")
    public EmissionResult tick() {
        final List<EmittedEvent> emitted = new ArrayList<>();
        final List<EmissionFailure> failures = new ArrayList<>();

        for (final EventSourceConfig source : sources) {
            final String qualifiedName = source.qualifiedName();
            try {
                final Optional<SimulationStrategy<EventTrigger, CloudEvent>> strategy =
                        simulation.strategyFor(qualifiedName);
                if (strategy.isEmpty()) {
                    continue;
                }

                final EventTrigger trigger = new EventTrigger(
                        source.eventType(), source.tenancyId(), Map.of());

                if (!strategy.get().canResolve(trigger)) {
                    continue;
                }

                final CloudEvent event = strategy.get().resolve(trigger);
                final CloudEvent stamped = CloudEventBuilder.from(event)
                        .withId(UUID.randomUUID().toString())
                        .withTime(OffsetDateTime.now())
                        .build();
                eventSink.accept(stamped);
                emitted.add(new EmittedEvent(qualifiedName, stamped));
            } catch (final Exception e) {
                failures.add(new EmissionFailure(qualifiedName, e));
            }
        }

        return new EmissionResult(emitted, failures);
    }

    public static KeyExtractor<EventTrigger> defaultKeyExtractor() {
        return trigger -> trigger.eventType() + "::" + trigger.tenancyId();
    }
}
