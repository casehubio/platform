package io.casehub.platform.api.datasource;

/**
 * Thrown by {@link Marshaller#marshal(Object)} when marshalling fails.
 *
 * <p>This is a checked exception — callers must handle marshalling failures explicitly.
 */
public class MarshalException extends Exception {

    public MarshalException(String message) {
        super(message);
    }

    public MarshalException(String message, Throwable cause) {
        super(message, cause);
    }
}
