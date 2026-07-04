package io.casehub.platform.api.datasource;

import java.util.function.Predicate;

/**
 * Ingestion entry point and subscription hub for typed event streams.
 *
 * <p>Alpha network clients subscribe via {@link #subscribe(DataProcessor)} and variants.
 * Events added via {@link #add(Object)} (inherited from {@link DataProcessor}) propagate
 * through the network to all matching subscriptions.
 *
 * <p>Four subscription variants provide increasing specificity:
 * <ol>
 *   <li>{@link #subscribe(DataProcessor)} — all objects</li>
 *   <li>{@link #subscribe(ObjectType, DataProcessor)} — type-filtered</li>
 *   <li>{@link #subscribe(ObjectType, Predicate, DataProcessor)} — type + predicate-filtered</li>
 *   <li>{@link #subscribe(Class, Predicate, DataProcessor)} — convenience for {@link ClassObjectType}</li>
 * </ol>
 *
 * <p>Subscriptions are tenant-isolated when the DataSource is case-scoped; platform-global
 * DataSources broadcast to all subscribers.
 *
 * @param <T> the base type this DataSource handles
 */
public interface DataSource<T> extends DataProcessor<T> {

    /**
     * Subscribe to all objects added to this DataSource.
     *
     * @param processor the processor to invoke on every added object
     * @return handle to unsubscribe
     */
    SubscriptionHandle subscribe(DataProcessor<? super T> processor);

    /**
     * Subscribe to objects matching {@code objectType}.
     *
     * <p>Only objects where {@code objectType.matches(object)} returns {@code true}
     * are delivered to the processor.
     *
     * @param objectType type filter
     * @param processor  the processor to invoke on matching objects
     * @param <U>        the specific subtype being subscribed to
     * @return handle to unsubscribe
     */
    <U> SubscriptionHandle subscribe(ObjectType<U> objectType, DataProcessor<? super U> processor);

    /**
     * Subscribe to objects matching both {@code objectType} and {@code filter}.
     *
     * <p>Only objects where both {@code objectType.matches(object)} and
     * {@code filter.test(object)} return {@code true} are delivered.
     *
     * @param objectType type filter
     * @param filter     predicate filter
     * @param processor  the processor to invoke on matching objects
     * @param <U>        the specific subtype being subscribed to
     * @return handle to unsubscribe
     */
    <U> SubscriptionHandle subscribe(ObjectType<U> objectType, Predicate<U> filter, DataProcessor<? super U> processor);

    /**
     * Convenience variant using {@link ClassObjectType}.
     *
     * <p>Equivalent to {@code subscribe(new ClassObjectType<>(type), filter, processor)}.
     *
     * @param type      Java class filter
     * @param filter    predicate filter
     * @param processor the processor to invoke on matching objects
     * @param <U>       the specific subtype being subscribed to
     * @return handle to unsubscribe
     */
    <U> SubscriptionHandle subscribe(Class<U> type, Predicate<U> filter, DataProcessor<? super U> processor);
}
