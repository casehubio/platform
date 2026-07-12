package io.casehub.platform.datasource.alpha;

import io.casehub.platform.api.datasource.DataProcessor;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out processor — delivers to multiple subscribers, isolating exceptions.
 *
 * <p>Each subscriber's {@link DataProcessor#add(Object)} call is wrapped in try/catch.
 * Exceptions are WARN-logged but do not block delivery to other subscribers.
 *
 * @param <T> the type of objects this processor accepts
 */
final class FanOutProcessor<T> implements DataProcessor<T> {

    private static final Logger LOG = Logger.getLogger(FanOutProcessor.class);

    private final List<DataProcessor<? super T>> subscribers = new CopyOnWriteArrayList<>();

    void addSubscriber(DataProcessor<? super T> processor) {
        subscribers.add(processor);
    }

    void removeSubscriber(DataProcessor<? super T> processor) {
        subscribers.remove(processor);
    }

    boolean isEmpty() {
        return subscribers.isEmpty();
    }

    @Override
    public void add(T object) {
        for (DataProcessor<? super T> processor : subscribers) {
            try {
                processor.add(object);
            } catch (Exception e) {
                LOG.warnf(e, "Subscriber failed to process object of type %s",
                        object == null ? "null" : object.getClass().getName());
            }
        }
    }
}
