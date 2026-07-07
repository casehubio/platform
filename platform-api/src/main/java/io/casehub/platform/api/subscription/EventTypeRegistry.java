package io.casehub.platform.api.subscription;

import java.util.Optional;
import java.util.Set;

/**
 * Registry of discoverable event types. Domain bridges self-register their
 * {@link EventTypeDescriptor} records at {@code @PostConstruct} / {@code @Startup}.
 *
 * <p>Append-only at startup — no deregistration needed. Populated from domain
 * modules (casehub-work, casehub-engine, etc.), consumed by the subscription
 * REST API and notification center UI.
 */
public interface EventTypeRegistry {

    void register(EventTypeDescriptor descriptor);

    Optional<EventTypeDescriptor> resolve(String eventType);

    Set<EventTypeDescriptor> discover();
}
