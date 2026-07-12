package io.casehub.platform.datasource.alpha;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataProcessor;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.ObjectType;
import io.casehub.platform.api.datasource.SubscriptionHandle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <h2>Reference counting</h2>
 * <p>{@link #markForRemoval(Runnable)} marks this DataSource for deferred cleanup.
 * The callback fires when the share count (active subscriber count) reaches zero.
 * Subscriptions after marking are accepted — consistent with reference counting
 * semantics. The DataSource is only cleaned up when all subscribers (including those
 * added during drain) have unsubscribed.
 *
 * <h2>Error isolation</h2>
 * <p>Each subscriber's {@link DataProcessor#add(Object)} call is wrapped in try/catch.
 * Exceptions are WARN-logged but do not block delivery to other subscribers.
 *
 * @param <T> the base type this DataSource handles
 */
public final class AlphaDataSource<T> implements DataSource<T> {

    private final FanOutProcessor<T>       directSubscribers = new FanOutProcessor<>();
    private final Map<Object, TypeNode<?>> typeNodes         = new ConcurrentHashMap<>();
    private final AtomicInteger            shareCount        = new AtomicInteger(0);
    private volatile boolean pendingRemoval = false;
    private Runnable onEmpty;

    @Override
    public SubscriptionHandle subscribe(DataProcessor<? super T> processor) {
        shareCount.incrementAndGet();
        directSubscribers.addSubscriber(processor);
        return new Handle(() -> directSubscribers.removeSubscriber(processor), this);
    }

    @Override
    public <U> SubscriptionHandle subscribe(ObjectType<U> objectType, DataProcessor<? super U> processor) {
        shareCount.incrementAndGet();
        TypeNode<U> node = getOrCreateTypeNode(objectType);
        node.addNoFilterSubscriber(processor);
        return new Handle(() -> {
            node.removeNoFilterSubscriber(processor);
            if (node.isEmpty()) {
                typeNodes.remove(objectType.getTypeKey());
            }
        }, this);
    }

    @Override
    public <U> SubscriptionHandle subscribe(ObjectType<U> objectType, Predicate<U> filter,
                                             DataProcessor<? super U> processor) {
        shareCount.incrementAndGet();
        TypeNode<U> node = getOrCreateTypeNode(objectType);
        node.addSubscriber(filter, processor);
        return new Handle(() -> {
            node.removeSubscriber(filter, processor);
            if (node.isEmpty()) {
                typeNodes.remove(objectType.getTypeKey());
            }
        }, this);
    }

    @Override
    public <U> SubscriptionHandle subscribe(Class<U> type, Predicate<U> filter,
                                             DataProcessor<? super U> processor) {
        return subscribe(new ClassObjectType<>(type), filter, processor);
    }

    @Override
    public void add(T object) {
        directSubscribers.add(object);
        for (TypeNode<?> node : typeNodes.values()) {
            node.add(object);
        }
    }

    /**
     * Marks this DataSource for deferred removal. The callback fires when the share
     * count reaches zero — immediately if already zero, otherwise when the last
     * subscriber unsubscribes.
     */
    public void markForRemoval(Runnable onEmpty) {
        synchronized (this) {
            this.pendingRemoval = true;
            this.onEmpty = onEmpty;
            if (shareCount.get() == 0) {
                onEmpty.run();
            }
        }
    }

    public boolean isPendingRemoval() {
        return pendingRemoval;
    }

    private void onSubscriberRemoved() {
        synchronized (this) {
            if (shareCount.decrementAndGet() == 0 && pendingRemoval) {
                onEmpty.run();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <U> TypeNode<U> getOrCreateTypeNode(ObjectType<U> objectType) {
        return (TypeNode<U>) typeNodes.computeIfAbsent(
                objectType.getTypeKey(),
                k -> new TypeNode<>(objectType)
        );
    }

    private static final class Handle implements SubscriptionHandle {

        private final AtomicBoolean active = new AtomicBoolean(true);
        private final Runnable unsubscribeAction;
        private final AlphaDataSource<?> owner;

        Handle(Runnable unsubscribeAction, AlphaDataSource<?> owner) {
            this.unsubscribeAction = unsubscribeAction;
            this.owner = owner;
        }

        @Override
        public void unsubscribe() {
            if (active.compareAndSet(true, false)) {
                unsubscribeAction.run();
                owner.onSubscriberRemoved();
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }
}
