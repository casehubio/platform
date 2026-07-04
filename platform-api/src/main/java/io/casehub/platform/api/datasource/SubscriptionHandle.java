package io.casehub.platform.api.datasource;

/**
 * Handle returned by {@link DataSource#subscribe(DataProcessor)} variants.
 *
 * <p>Use {@link #unsubscribe()} to stop receiving further objects.
 */
public interface SubscriptionHandle {

    /**
     * Unsubscribe the processor from the data source.
     *
     * <p>Idempotent — calling multiple times has no effect after the first call.
     * The subscription is removed from the alpha network and the processor receives
     * no further objects.
     */
    void unsubscribe();

    /**
     * Returns {@code true} if the subscription is active.
     *
     * <p>Becomes {@code false} after {@link #unsubscribe()} is called.
     */
    boolean isActive();
}
