package io.casehub.platform.simulation.event.quarkus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.TemporalDriverFactory;
import io.casehub.platform.simulation.TemporalSimulationDriver;
import io.casehub.platform.simulation.event.EventSequenceRunner;
import io.casehub.platform.simulation.event.EventSourceConfig;
import io.casehub.platform.simulation.event.SimulatedEventEmitter;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.UncheckedIOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    public TemporalDriverFactory<Map<String, Object>> temporalDriverFactory() {
        return () -> new TemporalSimulationDriver<>(
                (qualifiedName, label, payload) -> {
                    try {
                        CloudEvent ce = CloudEventBuilder.v1()
                                .withType(qualifiedName)
                                .withId(UUID.randomUUID().toString())
                                .withSource(URI.create("//simulation"))
                                .withTime(OffsetDateTime.now())
                                .withData("application/json", JSON_MAPPER.writeValueAsBytes(payload))
                                .build();
                        cloudEventBus.fireAsync(ce);
                    } catch (JsonProcessingException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                simulation);
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
