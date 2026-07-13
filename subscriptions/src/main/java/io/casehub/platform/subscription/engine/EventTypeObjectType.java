package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.ObjectType;
import io.casehub.platform.api.subscription.SubscribableEvent;

import java.util.Objects;

/**
 * Custom {@link ObjectType} for event type string discrimination via the
 * {@link SubscribableEvent} interface — compile-time enforced, no reflection.
 *
 * <p>Supports exact matching ({@code io.casehub.work.workitem.completed}) and
 * prefix glob matching ({@code io.casehub.work.workitem.*}). A trailing
 * {@code .*} matches any event type that starts with the prefix before the
 * wildcard. A bare {@code *} matches all event types.
 *
 * <p>The alpha network uses {@link #getTypeKey()} for type-based routing and
 * {@link #matches(Object)} for runtime type checking against incoming events.
 */
public final class EventTypeObjectType implements ObjectType<Object> {

    private final String eventType;
    private final String prefix;

    public EventTypeObjectType(final String eventType) {
        this.eventType = Objects.requireNonNull(eventType);
        if (eventType.equals("*")) {
            this.prefix = "";
        } else if (eventType.endsWith(".*")) {
            this.prefix = eventType.substring(0, eventType.length() - 1);
        } else {
            this.prefix = null;
        }
    }

    @Override
    public boolean matches(final Object object) {
        final String pojoType = extractEventType(object);
        if (pojoType == null) {return false;}
        if (prefix != null) {
            return pojoType.startsWith(prefix) && pojoType.length() > prefix.length();
        }
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
