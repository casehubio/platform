package io.casehub.platform.api.datasource;

/**
 * Transforms objects from one type to another.
 *
 * <p>Used as a pre-processing decorator on {@link DataSource#add(Object)} when
 * configured via {@link DataSourceDescriptor#marshallerKeys()}. Typical use case:
 * unmarshalling {@code CloudEvent} payloads into domain objects before alpha
 * network routing.
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
