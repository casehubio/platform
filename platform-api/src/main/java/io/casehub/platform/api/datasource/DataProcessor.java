package io.casehub.platform.api.datasource;

/**
 * Consumes typed objects pushed from a {@link DataSource}.
 *
 * <p>Alpha network nodes implement this interface to receive filtered or transformed
 * data from upstream sources. Processors are registered via
 * {@link DataSource#subscribe(DataProcessor)} and variants.
 *
 * @param <T> the type of objects this processor accepts
 */
public interface DataProcessor<T> {

    /**
     * Process a single object.
     *
     * <p>Implementations must be non-blocking or delegate to async executors.
     * Throwing an exception does not stop subsequent deliveries to other processors.
     */
    void add(T object);
}
