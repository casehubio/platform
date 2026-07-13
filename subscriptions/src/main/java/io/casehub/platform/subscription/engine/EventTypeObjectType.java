package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.ObjectType;
import io.casehub.platform.api.subscription.SubscribableEvent;

import java.util.Objects;

/**
 * Custom {@link ObjectType} for event type string discrimination via the
 * {@link SubscribableEvent} interface — compile-time enforced, no reflection.
 *
 * <p>The alpha network uses {@link #getTypeKey()} for type-based routing and
 * {@link #matches(Object)} for runtime type checking against incoming events.
 */
public final class EventTypeObjectType implements ObjectType<Object> {

    private final String eventType;

    public EventTypeObjectType(final String eventType) {
        this.eventType = Objects.requireNonNull(eventType);
    }

    @Override
    public boolean matches(final Object object) {
        final String pojoType = extractEventType(object);
        return eventType.equals(pojoType);
    }

    @Override
    public Object getTypeKey() {
        return eventType;
    }

    static String extractEventType(final Object object) {
        if (object instanceof SubscribableEvent event) {
            return event.type();
        }
        return null;
    }
}
