package io.casehub.platform.datasource;

import io.casehub.platform.api.datasource.DataProcessor;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceQuery;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.datasource.ObjectType;
import io.casehub.platform.api.datasource.SubscriptionHandle;
import io.casehub.platform.api.path.Path;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * No-op {@link DataSourceRegistry} — active when no backend module is on the classpath.
 *
 * <p>{@link #register(DataSourceDescriptor)} returns a stub {@link DataSource} that accepts
 * {@code add()} calls (silently dropped) and {@code subscribe()} calls (returns inert handle).
 * {@link #resolve(Path, String)} and {@link #resolveSource(Path, String)} always return empty.
 * {@link #discover(DataSourceQuery)} always returns an empty list.
 * {@link #deregister(Path, String)} is a silent no-op.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link DataSourceRegistry} implementation on the classpath, per the
 * {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpDataSourceRegistry implements DataSourceRegistry {

    @Override
    public DataSource<?> register(final DataSourceDescriptor descriptor) {
        return NoOpDataSource.INSTANCE;
    }

    /**
     * Stub {@link DataSource} that accepts all operations and does nothing.
     */
    private enum NoOpDataSource implements DataSource<Object> {
        INSTANCE;

        @Override
        public void add(Object value) {
            // Silent no-op
        }

        @Override
        public SubscriptionHandle subscribe(DataProcessor<? super Object> processor) {
            return NoOpSubscriptionHandle.INSTANCE;
        }

        @Override
        public <U> SubscriptionHandle subscribe(ObjectType<U> objectType, DataProcessor<? super U> processor) {
            return NoOpSubscriptionHandle.INSTANCE;
        }

        @Override
        public <U> SubscriptionHandle subscribe(ObjectType<U> objectType, Predicate<U> filter, DataProcessor<? super U> processor) {
            return NoOpSubscriptionHandle.INSTANCE;
        }

        @Override
        public <U> SubscriptionHandle subscribe(Class<U> type, Predicate<U> filter, DataProcessor<? super U> processor) {
            return NoOpSubscriptionHandle.INSTANCE;
        }
    }

    /**
     * Inert {@link SubscriptionHandle} — always reports inactive, unsubscribe is silent no-op.
     */
    private enum NoOpSubscriptionHandle implements SubscriptionHandle {
        INSTANCE;

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void unsubscribe() {
            // Silent no-op
        }
    }

    @Override
    public Optional<DataSourceDescriptor> resolve(final Path path, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public Optional<DataSource<?>> resolveSource(final Path path, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public List<DataSourceDescriptor> discover(final DataSourceQuery query) {
        return List.of();
    }

    @Override
    public void deregister(final Path path, final String tenancyId) {}

    @Override
    public void update(final DataSourceDescriptor descriptor) {
    }

}
