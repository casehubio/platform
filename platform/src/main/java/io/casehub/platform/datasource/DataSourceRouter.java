package io.casehub.platform.datasource;

import io.casehub.platform.api.datasource.DataSource;
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
 * <p>Startup: {@code @Observes StartupEvent} replays queued {@link DataSourceRegistered} events
 * (from {@code @Startup} beans) and sets {@code started = true}. All DataSource wiring happens
 * via {@link DataSourceRegistered} events — pre-startup events are queued and replayed at startup.
 *
 * <p>Runtime: {@code @ObservesAsync DataSourceRegistered} wires new DataSources. Idempotent:
 * if already wired, skips. {@code @ObservesAsync CloudEvent} routes to all matching DataSources.
 *
 * <p>Routing logic:
 * <ol>
 *   <li>Extract {@code tenancyid} extension from CloudEvent</li>
 *   <li>For each wired DataSource: check tenancy match (tenant-specific OR platform-global)</li>
 *   <li>Check {@link DataSourceDescriptor#acceptedEventTypes()} pre-filter (if non-empty)</li>
 *   <li>Call {@link DataSource#add(Object)} — alpha network propagates to subscribers</li>
 * </ol>
 *
 * <p>When {@link io.casehub.platform.datasource.NoOpDataSourceRegistry} is active, no DataSources
 * exist, so routing is silent no-op.
 */
@ApplicationScoped
public class DataSourceRouter {

    private static final Logger LOG = Logger.getLogger(DataSourceRouter.class);

    private final DataSourceRegistry registry;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<WiredDataSource> wiredDataSources = new CopyOnWriteArrayList<>();
    private final List<DataSourceRegistered> pendingEvents = new ArrayList<>();

    @Inject
    public DataSourceRouter(DataSourceRegistry registry) {
        this.registry = registry;
    }

    public void onStartup(@Observes StartupEvent ev) {
        synchronized (pendingEvents) {
            pendingEvents.forEach(this::wireRoute);
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

    private void wireRoute(DataSourceRegistered event) {
        DataSourceDescriptor descriptor = event.descriptor();
        // Idempotent: skip if already wired
        if (wiredDataSources.stream()
                .anyMatch(w -> w.descriptor.path().equals(descriptor.path())
                        && w.descriptor.tenancyId().equals(descriptor.tenancyId()))) {
            return;
        }

        registry.resolveSource(descriptor.path(), descriptor.tenancyId())
                .ifPresentOrElse(
                        dataSource -> {
                            wiredDataSources.add(new WiredDataSource(descriptor, dataSource));
                            LOG.debugf("Wired DataSource: path=%s, tenancyId=%s",
                                    descriptor.path(), descriptor.tenancyId());
                        },
                        () -> LOG.warnf("DataSourceRegistered event fired but resolveSource returned empty: path=%s, tenancyId=%s",
                                descriptor.path(), descriptor.tenancyId())
                );
    }

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

            // Tenancy match: tenant-specific OR platform-global
            boolean tenancyMatch = descriptor.tenancyId().equals(tenancyId)
                    || descriptor.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID);
            if (!tenancyMatch) {
                continue;
            }

            // acceptedEventTypes pre-filter
            if (!descriptor.acceptedEventTypes().isEmpty()
                    && !descriptor.acceptedEventTypes().contains(eventType)) {
                continue;
            }

            // Route to DataSource
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
