package io.casehub.platform.streams.camel;

import io.casehub.platform.api.endpoints.EndpointRegistered;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;

/**
 * Dynamic Camel route builder for runtime-registered CAMEL endpoints.
 *
 * <p>Startup: {@code @Observes StartupEvent} discovers all pre-startup CAMEL endpoints
 * from the registry and adds routes. Sets {@code camelStarted = true} after processing.
 *
 * <p>Runtime: {@code @ObservesAsync EndpointRegistered} for CAMEL protocol — if
 * {@code camelStarted} is false, pre-startup events delivered late by the CDI async executor
 * are discarded (the startup handler already covered them via discover). If
 * {@code camelStarted} is true, adds a route idempotently via the {@code routedUris} set.
 *
 * <p><b>P0 constraint:</b> Changing a Camel endpoint URI requires restart — the old route
 * is not stopped; a second route is added for the new URI.
 *
 * <p><b>Known startup-window gap:</b> An endpoint registered after {@code onStartup}'s
 * {@code discover()} but before {@code camelStarted.set(true)} would be discarded.
 * In practice this window is zero (desiredstate reconciliation does not start before the
 * app is ready).
 *
 * <p>CAMEL and KAFKA are mutually exclusive for the same Kafka topic — running both
 * from the same consumer group causes silent message loss.
 */
@ApplicationScoped
public class CamelStreamProcessor {

    @Inject
    EndpointRegistry endpointRegistry;

    @Inject
    Event<CloudEvent> cloudEventBus;

    @Inject
    CamelContext camelContext;

    private CamelStreamProcessorCore core;

    void onStartup(@Observes StartupEvent ev) {
        core = new CamelStreamProcessorCore(camelContext, endpointRegistry,
            ce -> cloudEventBus.fireAsync(ce));
        core.init();
    }

    void onEndpointRegistered(@ObservesAsync EndpointRegistered event) {
        if (core != null) {
            core.onEndpointRegistered(event.descriptor());
        }
    }
}
