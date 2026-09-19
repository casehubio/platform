package io.casehub.platform.streams.poll;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@Startup
@ApplicationScoped
public class PollStreamProcessor {

    @Inject
    EndpointRegistry endpointRegistry;

    @Inject
    Event<CloudEvent> cloudEventBus;

    private PollStreamProcessorCore core;

    void init() {
        core = new PollStreamProcessorCore(endpointRegistry,
            ce -> cloudEventBus.fireAsync(ce));
    }

    @Scheduled(every = "${casehub.streams.poll.interval:60s}")
    void poll() {
        if (core == null) init();
        core.poll();
    }
}
