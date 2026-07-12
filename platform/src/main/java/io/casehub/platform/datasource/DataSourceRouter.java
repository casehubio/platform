package io.casehub.platform.datasource;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDeregistered;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceRegistered;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CDI bridge routing {@code @ObservesAsync CloudEvent} events to registered DataSources.
 *
 * <p>Startup: {@code @Observes StartupEvent} replays queued events (both
 * {@link DataSourceRegistered} and {@link DataSourceDeregistered}) in order, then sets
 * {@code started = true}.
 *
 * <p>Runtime: {@code @ObservesAsync DataSourceRegistered} wires new DataSources using
 * convergent logic — resolves the current DataSource from the registry, replaces stale
 * entries, and skips if the DataSource was already deregistered. {@code @ObservesAsync
 * DataSourceDeregistered} unwires routes using identity comparison against the deregistered
 * DataSource instance, preventing removal of replacement entries.
 *
 * <p>Both handlers are convergent — they produce the correct wired state regardless of
 * CDI event processing order.
 *
 * <p>Routing logic:
 * <ol>
 *   <li>Extract {@code tenancyid} extension from CloudEvent</li>
 *   <li>For each wired DataSource: check tenancy match (tenant-specific OR platform-global)</li>
 *   <li>Check {@link DataSourceDescriptor#acceptedEventTypes()} pre-filter (if non-empty)</li>
 *   <li>Call {@link DataSource#add(Object)} — alpha network propagates to subscribers</li>
 * </ol>
 */
@ApplicationScoped
public class DataSourceRouter {

    private static final Logger LOG = Logger.getLogger(DataSourceRouter.class);

    private final DataSourceRegistry registry;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<WiredDataSource> wiredDataSources = new CopyOnWriteArrayList<>();
    private final List<Object> pendingEvents = new ArrayList<>();

    @Inject
    public DataSourceRouter(DataSourceRegistry registry) {
        this.registry = registry;
    }

    public void onStartup(@Observes StartupEvent ev) {
        synchronized (pendingEvents) {
            for (Object event : pendingEvents) {
                if (event instanceof DataSourceRegistered r) {
                    wireRoute(r);
                } else if (event instanceof DataSourceDeregistered d) {
                    unwireRoute(d);
                }
            }
            pendingEvents.clear();
        }
        started.set(true);
        LOG.debug("DataSourceRouter started");
    }

    public void onDataSourceRegistered(@ObservesAsync DataSourceRegistered event) {
        if (!started.get()) {
            synchronized (pendingEvents) {
                pendingEvents.add(event);
            }
            return;
        }
        wireRoute(event);
    }

    public void onDataSourceDeregistered(@ObservesAsync DataSourceDeregistered event) {
        if (!started.get()) {
            synchronized (pendingEvents) {
                pendingEvents.add(event);
            }
            return;
        }
        unwireRoute(event);
    }

    private void wireRoute(DataSourceRegistered event) {
        DataSourceDescriptor descriptor = event.descriptor();

        var resolved = registry.resolveSource(descriptor.path(), descriptor.tenancyId());
        if (resolved.isEmpty()) {
            LOG.debugf("DataSourceRegistered but resolveSource empty (deregistered?): path=%s, tenancyId=%s",
                    descriptor.path(), descriptor.tenancyId());
            return;
        }

        DataSource<?> dataSource = resolved.get();

        wiredDataSources.removeIf(w ->
                w.descriptor.path().equals(descriptor.path())
                && w.descriptor.tenancyId().equals(descriptor.tenancyId())
                && w.dataSource != dataSource);

        boolean alreadyWired = wiredDataSources.stream()
                .anyMatch(w -> w.descriptor.path().equals(descriptor.path())
                        && w.descriptor.tenancyId().equals(descriptor.tenancyId())
                        && w.dataSource == dataSource);

        if (!alreadyWired) {
            wiredDataSources.add(new WiredDataSource(descriptor, dataSource));
            LOG.debugf("Wired DataSource: path=%s, tenancyId=%s",
                    descriptor.path(), descriptor.tenancyId());
        }
    }

    private void unwireRoute(DataSourceDeregistered event) {
        wiredDataSources.removeIf(w ->
                w.descriptor.path().equals(event.descriptor().path())
                && w.descriptor.tenancyId().equals(event.descriptor().tenancyId())
                && w.dataSource == event.dataSource());
    }

    @SuppressWarnings("unchecked")
    public void onCloudEvent(@ObservesAsync CloudEvent cloudEvent) {
        Object tenancyIdObj = cloudEvent.getExtension("tenancyid");
        if (tenancyIdObj == null) {
            LOG.debugf("CloudEvent missing tenancyid extension, skipping routing: id=%s", cloudEvent.getId());
            return;
        }
        String tenancyId = tenancyIdObj.toString();
        String eventType = cloudEvent.getType();

        for (WiredDataSource wired : wiredDataSources) {
            DataSourceDescriptor descriptor = wired.descriptor;

            boolean tenancyMatch = descriptor.tenancyId().equals(tenancyId)
                    || descriptor.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID);
            if (!tenancyMatch) {
                continue;
            }

            if (!descriptor.acceptedEventTypes().isEmpty()
                    && !descriptor.acceptedEventTypes().contains(eventType)) {
                continue;
            }

            try {
                ((DataSource<Object>) wired.dataSource).add(cloudEvent);
            } catch (ClassCastException e) {
                LOG.warnf("DataSource type mismatch for path=%s, tenancyId=%s: expected CloudEvent-compatible, got %s",
                        descriptor.path(), descriptor.tenancyId(), descriptor.objectType());
            }
        }
    }

    private record WiredDataSource(DataSourceDescriptor descriptor, DataSource<?> dataSource) {
    }
}
