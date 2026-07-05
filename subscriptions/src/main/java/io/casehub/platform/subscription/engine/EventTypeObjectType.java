package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.ObjectType;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

/**
 * Custom {@link ObjectType} for event type string discrimination. Uses MethodHandle
 * to reflectively call {@code type()} on POJOs — decoupled from any specific event class.
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

    /**
     * Extracts the event type string from a POJO by invoking its {@code type()} method
     * via MethodHandle. Returns {@code null} if the object is null, has no {@code type()}
     * method, or the method does not return a String.
     */
    static String extractEventType(final Object object) {
        if (object == null) {
            return null;
        }
        try {
            var method = object.getClass().getMethod("type");
            if (method.getReturnType() != String.class) {
                return null;
            }
            var handle = MethodHandles.lookup().unreflect(method);
            return (String) handle.invoke(object);
        } catch (Throwable e) {
            return null;
        }
    }
}
