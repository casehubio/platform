package io.casehub.platform.datasource.memory;

import io.casehub.platform.api.datasource.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Alpha network implementation of {@link DataSource}.
 *
 * <p>Provides type discrimination, filter chains, and fan-out delivery. Subscribers
 * are registered via {@link #subscribe(DataProcessor)} variants and delivered to via
 * {@link #add(Object)} (inherited from {@link DataProcessor}).
 *
 * <h2>Network architecture</h2>
 * <ol>
 *   <li>Direct subscribers (no type/filter) receive every event</li>
 *   <li>Type nodes (keyed by {@link ObjectType#getTypeKey()}) discriminate by type</li>
 *   <li>Filter chains under each type node evaluate predicates</li>
 *   <li>Fan-out: multiple subscribers under same type+filter get the event</li>
 * </ol>
 *
 * <p>Type nodes are shared automatically (same {@code getTypeKey()} = same node).
 * FilterExpression nodes are shared when {@code type()} and {@code expression()} match.
 * Plain predicates are never shared.
 *
 * <h2>Error isolation</h2>
 * <p>Each subscriber's {@link DataProcessor#add(Object)} call is wrapped in try/catch.
 * Exceptions are WARN-logged but do not block delivery to other subscribers.
 *
 * @param <T> the base type this DataSource handles
 */
public final class AlphaDataSource<T> implements DataSource<T> {

    private final FanOutProcessor<T> directSubscribers = new FanOutProcessor<>();
    private final Map<Object, TypeNode<?>> typeNodes = new ConcurrentHashMap<>();

    @Override
    public SubscriptionHandle subscribe(DataProcessor<? super T> processor) {
        directSubscribers.addSubscriber(processor);
        return new Handle<>(() -> directSubscribers.removeSubscriber(processor));
    }

    @Override
    public <U> SubscriptionHandle subscribe(ObjectType<U> objectType, DataProcessor<? super U> processor) {
        TypeNode<U> node = getOrCreateTypeNode(objectType);
        node.addNoFilterSubscriber(processor);
        return new Handle<>(() -> {
            node.removeNoFilterSubscriber(processor);
            if (node.isEmpty()) {
                typeNodes.remove(objectType.getTypeKey());
            }
        });
    }

    @Override
    public <U> SubscriptionHandle subscribe(ObjectType<U> objectType, Predicate<U> filter,
                                             DataProcessor<? super U> processor) {
        TypeNode<U> node = getOrCreateTypeNode(objectType);
        node.addSubscriber(filter, processor);
        return new Handle<>(() -> {
            node.removeSubscriber(filter, processor);
            if (node.isEmpty()) {
                typeNodes.remove(objectType.getTypeKey());
            }
        });
    }

    @Override
    public <U> SubscriptionHandle subscribe(Class<U> type, Predicate<U> filter,
                                             DataProcessor<? super U> processor) {
        return subscribe(new ClassObjectType<>(type), filter, processor);
    }

    @Override
    public void add(T object) {
        // Direct subscribers get everything
        directSubscribers.add(object);
        // Propagate through type nodes
        for (TypeNode<?> node : typeNodes.values()) {
            node.add(object);
        }
    }

    @SuppressWarnings("unchecked")
    private <U> TypeNode<U> getOrCreateTypeNode(ObjectType<U> objectType) {
        return (TypeNode<U>) typeNodes.computeIfAbsent(
                objectType.getTypeKey(),
                k -> new TypeNode<>(objectType)
        );
    }

    /**
     * Subscription handle implementation.
     */
    private static final class Handle<T> implements SubscriptionHandle {

        private final AtomicBoolean active = new AtomicBoolean(true);
        private final Runnable unsubscribeAction;

        Handle(Runnable unsubscribeAction) {
            this.unsubscribeAction = unsubscribeAction;
        }

        @Override
        public void unsubscribe() {
            if (active.compareAndSet(true, false)) {
                unsubscribeAction.run();
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }
}
