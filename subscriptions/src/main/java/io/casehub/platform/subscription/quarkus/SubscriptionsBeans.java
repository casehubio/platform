package io.casehub.platform.subscription.quarkus;

import io.casehub.platform.api.subscription.EventTypeRegistry;
import io.casehub.platform.subscription.EventTypeService;
import io.casehub.platform.subscription.InMemoryEventTypeRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SubscriptionsBeans {

    @Produces
    @ApplicationScoped
    public InMemoryEventTypeRegistry inMemoryEventTypeRegistry() {
        return new InMemoryEventTypeRegistry();
    }

    @Produces
    @ApplicationScoped
    public EventTypeService eventTypeService(EventTypeRegistry registry) {
        return new EventTypeService(registry);
    }
}
