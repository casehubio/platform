package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataProcessor;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.datasource.SubscriptionHandle;
import io.casehub.platform.api.notification.NotificationStore;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionCreated;
import io.casehub.platform.api.subscription.SubscriptionDeleted;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionUpdated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;

/**
 * Core subscription engine — registers the notification DataSource at startup,
 * loads all enabled subscriptions, compiles each into a FilterExpression, wires
 * each as an alpha network subscriber, and handles dynamic mutations via CDI
 * event observers.
 *
 * <p>On match, resolves the template against the event POJO and stores the
 * resulting notification via {@link NotificationStore}.
 *
 * <h2>Thread safety</h2>
 * <p>Uses {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}
 * for per-key atomicity on dynamic mutations — prevents ghost subscriptions from
 * concurrent {@code @ObservesAsync} mutations (R2-03 design review finding).
 *
 * <h2>Tenant isolation</h2>
 * <p>Unconditional — injected into every FilterExpression by
 * {@link ConstraintCompiler}. The DataSource itself is platform-global; tenancy
 * filtering happens at the filter node level.
 */
@ApplicationScoped
public class SubscriptionEngine {

    private static final Logger LOG = Logger.getLogger(SubscriptionEngine.class);

    private final DataSourceRegistry dataSourceRegistry;
    private final SubscriptionStore subscriptionStore;
    private final NotificationStore notificationStore;
    private final ConcurrentHashMap<String, SubscriptionHandle> handles = new ConcurrentHashMap<>();
    private volatile DataSource<Object> notificationDataSource;

    @Inject
    public SubscriptionEngine(final DataSourceRegistry dataSourceRegistry,
                               final SubscriptionStore subscriptionStore,
                               final NotificationStore notificationStore) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.subscriptionStore = subscriptionStore;
        this.notificationStore = notificationStore;
    }

    /**
     * Registers the notification DataSource and wires all enabled subscriptions
     * at application startup.
     */
    @SuppressWarnings("unchecked")
    void onStartup(@Observes final StartupEvent event) {
        var descriptor = new DataSourceDescriptor(
                NOTIFICATION_DATASOURCE_PATH,
                PLATFORM_TENANT_ID,
                new ClassObjectType<>(Object.class),
                null, Set.of(), Map.of());
        notificationDataSource = (DataSource<Object>) dataSourceRegistry.register(descriptor);

        LOG.info("Notification DataSource registered at " + NOTIFICATION_DATASOURCE_PATH);

        subscriptionStore.findAllEnabled().forEach(this::wireSubscription);

        LOG.infof("Subscription engine started — %d subscriptions wired", handles.size());
    }

    /**
     * Wires a subscription into the alpha network. Creates an {@link EventTypeObjectType}
     * for type discrimination, compiles the constraints into a tenant-isolated
     * {@link io.casehub.platform.api.datasource.FilterExpression}, and subscribes a
     * {@link DataProcessor} that resolves the template and stores the notification.
     *
     * <p>Uses {@code ConcurrentHashMap.compute()} for per-key atomicity — if a handle
     * already exists for this subscription ID, it is unsubscribed first.
     */
    private void wireSubscription(final Subscription subscription) {
        var objectType = new EventTypeObjectType(subscription.eventType());
        var filter = ConstraintCompiler.compile(
                subscription.constraints(), subscription.tenancyId(), subscription.userId());

        DataProcessor<Object> processor = pojo -> {
            var input = TemplateResolver.resolve(
                    subscription.template(), pojo,
                    subscription.userId(), subscription.tenancyId());
            if (input != null) {
                notificationStore.store(input);
            }
        };

        var handle = notificationDataSource.subscribe(objectType, filter, processor);

        handles.compute(subscription.id(), (id, existing) -> {
            if (existing != null) {
                existing.unsubscribe();
            }
            return handle;
        });
    }

    /**
     * Wires a subscription and returns the handle directly — used by
     * {@link #onUpdated(SubscriptionUpdated)} inside a {@code compute()} block
     * where the return value feeds the map.
     */
    private SubscriptionHandle wireAndReturnHandle(final Subscription subscription) {
        var objectType = new EventTypeObjectType(subscription.eventType());
        var filter = ConstraintCompiler.compile(
                subscription.constraints(), subscription.tenancyId(), subscription.userId());

        DataProcessor<Object> processor = pojo -> {
            var input = TemplateResolver.resolve(
                    subscription.template(), pojo,
                    subscription.userId(), subscription.tenancyId());
            if (input != null) {
                notificationStore.store(input);
            }
        };

        return notificationDataSource.subscribe(objectType, filter, processor);
    }

    /**
     * Wires a newly created subscription if enabled.
     */
    void onCreated(@ObservesAsync final SubscriptionCreated event) {
        if (event.subscription().enabled()) {
            wireSubscription(event.subscription());
            LOG.debugf("Wired new subscription %s", event.subscription().id());
        }
    }

    /**
     * Unwires the old subscription and wires the updated one if enabled.
     * Uses {@code compute()} for per-key atomicity — prevents ghost subscriptions.
     */
    void onUpdated(@ObservesAsync final SubscriptionUpdated event) {
        handles.compute(event.subscription().id(), (id, existing) -> {
            if (existing != null) {
                existing.unsubscribe();
            }
            if (event.subscription().enabled()) {
                return wireAndReturnHandle(event.subscription());
            }
            return null;
        });
        LOG.debugf("Rewired subscription %s (enabled=%s)",
                event.subscription().id(), event.subscription().enabled());
    }

    /**
     * Unwires a deleted subscription. Uses {@code compute()} for per-key atomicity.
     */
    void onDeleted(@ObservesAsync final SubscriptionDeleted event) {
        handles.compute(event.subscription().id(), (id, existing) -> {
            if (existing != null) {
                existing.unsubscribe();
            }
            return null;
        });
        LOG.debugf("Unwired deleted subscription %s", event.subscription().id());
    }
}
