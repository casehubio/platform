package io.casehub.platform.label.memory.quarkus;

import io.casehub.platform.api.label.LabelDeregistered;
import io.casehub.platform.api.label.LabelRegistered;
import io.casehub.platform.label.memory.InMemoryLabelRegistry;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class LabelInmemBeans {

    @Inject Event<LabelRegistered> registeredEvent;
    @Inject Event<LabelDeregistered> deregisteredEvent;

    @Produces
    @Alternative
    @Priority(100)
    @ApplicationScoped
    public InMemoryLabelRegistry inMemoryLabelRegistry() {
        return new InMemoryLabelRegistry(
                e -> registeredEvent.fireAsync(e),
                e -> deregisteredEvent.fireAsync(e));
    }
}
