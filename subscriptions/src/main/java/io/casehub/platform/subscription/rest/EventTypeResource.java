package io.casehub.platform.subscription.rest;

import io.casehub.platform.api.subscription.EventTypeDescriptor;
import io.casehub.platform.api.subscription.EventTypeRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Set;

@ApplicationScoped
@Path("/subscriptions/event-types")
public class EventTypeResource {

    private final EventTypeRegistry eventTypeRegistry;

    @Inject
    public EventTypeResource(final EventTypeRegistry eventTypeRegistry) {
        this.eventTypeRegistry = eventTypeRegistry;
    }

    @GET
    public Set<EventTypeDescriptor> listEventTypes() {
        return eventTypeRegistry.discover();
    }
}
