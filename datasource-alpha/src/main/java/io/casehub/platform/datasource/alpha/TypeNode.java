package io.casehub.platform.datasource.alpha;

import io.casehub.platform.api.datasource.DataProcessor;
import io.casehub.platform.api.datasource.FilterExpression;
import io.casehub.platform.api.datasource.ObjectType;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Type node in the alpha network — evaluates type match and routes through filter chain.
 *
 * <p>All subscribers under this type node share the same {@link ObjectType}. If the type
 * matches, the object is propagated through the filter chain (if any filters exist) or
 * directly to the no-filter subscribers.
 *
 * <p>Filter nodes are shareable when they wrap {@link FilterExpression} instances with
 * matching {@code type()} and {@code expression()}. Plain predicates get individual nodes.
 *
 * @param <T> the constrained type this node handles
 */
final class TypeNode<T> implements DataProcessor<Object> {

    private final ObjectType<T>       objectType;
    private final List<FilterNode<T>> filterNodes         = new CopyOnWriteArrayList<>();
    private final FanOutProcessor<T>  noFilterSubscribers = new FanOutProcessor<>();

    TypeNode(ObjectType<T> objectType) {
        this.objectType = Objects.requireNonNull(objectType, "objectType");
    }

    void addSubscriber(Predicate<T> filter, DataProcessor<? super T> processor) {
        FilterNode<T> node = findOrCreateFilterNode(filter);
        node.addSubscriber(processor);
    }

    void addNoFilterSubscriber(DataProcessor<? super T> processor) {
        noFilterSubscribers.addSubscriber(processor);
    }

    void removeSubscriber(Predicate<T> filter, DataProcessor<? super T> processor) {
        for (FilterNode<T> node : filterNodes) {
            if (filtersMatch(node.getFilter(), filter)) {
                node.removeSubscriber(processor);
                if (node.isEmpty()) {
                    filterNodes.remove(node);
                }
                return;
            }
        }
    }

    void removeNoFilterSubscriber(DataProcessor<? super T> processor) {
        noFilterSubscribers.removeSubscriber(processor);
    }

    boolean isEmpty() {
        return noFilterSubscribers.isEmpty() && filterNodes.isEmpty();
    }

    @Override
    public void add(Object object) {
        if (!objectType.matches(object)) {
            return;
        }
        @SuppressWarnings("unchecked")
        T typed = (T) object;
        // Deliver to no-filter subscribers
        noFilterSubscribers.add(typed);
        // Propagate through filter chain
        for (FilterNode<T> node : filterNodes) {
            node.add(typed);
        }
    }

    private FilterNode<T> findOrCreateFilterNode(Predicate<T> filter) {
        for (FilterNode<T> node : filterNodes) {
            if (filtersMatch(node.getFilter(), filter)) {
                return node;
            }
        }
        FilterNode<T> newNode = new FilterNode<>(filter);
        filterNodes.add(newNode);
        return newNode;
    }

    private boolean filtersMatch(Predicate<T> a, Predicate<T> b) {
        if (a == b) {
            return true;
        }
        // Share FilterExpression nodes if they have same type + expression
        if (a instanceof FilterExpression<?> fa && b instanceof FilterExpression<?> fb) {
            return Objects.equals(fa.type(), fb.type())
                    && Objects.equals(fa.expression(), fb.expression());
        }
        return false;
    }
}
