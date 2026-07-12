package io.casehub.platform.datasource.alpha;

import io.casehub.platform.api.datasource.DataProcessor;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Filter node in the alpha network — evaluates a predicate and delivers to subscribers.
 *
 * <p>All subscribers under this filter node share the same predicate. If the predicate
 * returns {@code true}, the object is delivered to all subscribers via the fan-out processor.
 *
 * @param <T> the type this filter operates on
 */
final class FilterNode<T> implements DataProcessor<T> {

    private final Predicate<T>       filter;
    private final FanOutProcessor<T> fanOut = new FanOutProcessor<>();

    FilterNode(Predicate<T> filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    Predicate<T> getFilter() {
        return filter;
    }

    void addSubscriber(DataProcessor<? super T> processor) {
        fanOut.addSubscriber(processor);
    }

    void removeSubscriber(DataProcessor<? super T> processor) {
        fanOut.removeSubscriber(processor);
    }

    boolean isEmpty() {
        return fanOut.isEmpty();
    }

    @Override
    public void add(T object) {
        if (filter.test(object)) {
            fanOut.add(object);
        }
    }
}
