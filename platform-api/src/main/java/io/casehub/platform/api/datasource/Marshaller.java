package io.casehub.platform.api.datasource;

/**
 * Transforms objects from one type to another in the alpha network.
 *
 * <p>Used by {@link io.casehub.platform.datasource.MarshallNode} (implementation detail)
 * to convert between types during event routing. Typical use case: unmarshalling
 * {@code CloudEvent} payloads into domain objects.
 *
 * @param <I> input type
 * @param <O> output type
 */
@FunctionalInterface
public interface Marshaller<I, O> {

    /**
     * Marshal {@code input} to the output type.
     *
     * @throws MarshalException if marshalling fails
     */
    O marshal(I input) throws MarshalException;
}
