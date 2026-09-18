package io.casehub.platform.simulation.event.quarkus;

import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.event.EventSequenceRunner;
import io.casehub.platform.simulation.event.EventSourceConfig;
import io.casehub.platform.simulation.event.SimulatedEventEmitter;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EventSimulationBeans {

    @Inject
    Event<CloudEvent> cloudEventBus;

    @Inject
    SimulationRuntime simulation;

    @Produces
    @ApplicationScoped
    public SimulatedEventEmitter emitter() {
        final List<EventSourceConfig> sources = loadSourcesFromConfig();
        return new SimulatedEventEmitter(
                simulation,
                event -> cloudEventBus.fireAsync(event),
                sources);
    }

    @Produces
    @ApplicationScoped
    public EventSequenceRunner sequenceRunner() {
        return new EventSequenceRunner(
                event -> cloudEventBus.fireAsync(event));
    }

    private List<EventSourceConfig> loadSourcesFromConfig() {
        final var config = ConfigProvider.getConfig();
        final List<EventSourceConfig> sources = new ArrayList<>();
        final String prefix = "casehub.simulation.event.sources.";

        for (final String name : config.getPropertyNames()) {
            if (name.startsWith(prefix) && name.endsWith(".event-type")) {
                final String sourceName = name.substring(prefix.length(),
                        name.length() - ".event-type".length());
                final String eventType = config.getValue(name, String.class);
                final String tenancyId = config.getOptionalValue(
                        prefix + sourceName + ".tenancy-id", String.class)
                        .orElse("default");
                final String qualifiedName = "event-emitter." + sourceName;
                sources.add(new EventSourceConfig(qualifiedName, eventType, tenancyId));
            }
        }

        return sources;
    }
}
