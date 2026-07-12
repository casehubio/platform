package io.casehub.platform.datasource.jpa;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDeregistered;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceQuery;
import io.casehub.platform.api.datasource.DataSourceRegistered;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.datasource.DataSourceUpdated;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.datasource.alpha.AlphaDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class JpaDataSourceRegistry implements DataSourceRegistry {

    private static final Logger LOG = Logger.getLogger(JpaDataSourceRegistry.class);

    private final ConcurrentHashMap<RegistryKey, DataSourceDescriptor> descriptorCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegistryKey, DataSource<?>> sources =
            new ConcurrentHashMap<>();

    @Inject
    EntityManager entityManager;

    @Inject
    Event<DataSourceRegistered> dataSourceRegisteredEvent;

    @Inject
    Event<DataSourceDeregistered> dataSourceDeregisteredEvent;

    @Inject
    Event<DataSourceUpdated> dataSourceUpdatedEvent;

    void onStartup(@Observes StartupEvent event) {
        List<DataSourceDescriptorEntity> entities = entityManager.createQuery(
                        "SELECT e FROM DataSourceDescriptorEntity e", DataSourceDescriptorEntity.class)
                .getResultList();

        for (DataSourceDescriptorEntity entity : entities) {
            DataSourceDescriptor descriptor = entity.toDomain();
            RegistryKey key = new RegistryKey(descriptor.path().value(), descriptor.tenancyId());
            AlphaDataSource<?> ds = new AlphaDataSource<>();
            descriptorCache.put(key, descriptor);
            sources.put(key, ds);
            dataSourceRegisteredEvent.fireAsync(new DataSourceRegistered(descriptor))
                    .whenComplete((e, t) -> {
                        if (t != null) {
                            LOG.warnf(t, "DataSourceRegistered observer failed for path %s",
                                    descriptor.path());
                        }
                    });
        }
        LOG.infof("DataSource JPA registry started — %d descriptor(s) reconciled", entities.size());
    }

    @Override
    @Transactional
    public DataSource<?> register(final DataSourceDescriptor descriptor) {
        final RegistryKey key = new RegistryKey(descriptor.path().value(), descriptor.tenancyId());

        DataSource<?> existing = sources.get(key);
        if (existing instanceof AlphaDataSource<?> alpha && !alpha.isPendingRemoval()) {
            return existing;
        }

        DataSourceDescriptorEntity entity = DataSourceDescriptorEntity.fromDomain(descriptor);
        entityManager.persist(entity);
        entityManager.flush();

        AlphaDataSource<?> ds = new AlphaDataSource<>();
        sources.put(key, ds);
        descriptorCache.put(key, descriptor);

        dataSourceRegisteredEvent.fireAsync(new DataSourceRegistered(descriptor))
                .whenComplete((e, t) -> {
                    if (t != null) {
                        LOG.warnf(t, "DataSourceRegistered observer failed for path %s",
                                descriptor.path());
                    }
                });

        return ds;
    }

    @Override
    public Optional<DataSourceDescriptor> resolve(final Path path, final String tenancyId) {
        DataSourceDescriptor tenant = descriptorCache.get(
                new RegistryKey(path.value(), tenancyId));
        if (tenant != null) return Optional.of(tenant);
        DataSourceDescriptor global = descriptorCache.get(
                new RegistryKey(path.value(), TenancyConstants.PLATFORM_TENANT_ID));
        return Optional.ofNullable(global);
    }

    @Override
    public Optional<DataSource<?>> resolveSource(final Path path, final String tenancyId) {
        DataSource<?> tenant = sources.get(new RegistryKey(path.value(), tenancyId));
        if (tenant != null) return Optional.of(tenant);
        DataSource<?> global = sources.get(
                new RegistryKey(path.value(), TenancyConstants.PLATFORM_TENANT_ID));
        return Optional.ofNullable(global);
    }

    @Override
    public List<DataSourceDescriptor> discover(final DataSourceQuery query) {
        return descriptorCache.values().stream()
                .filter(d -> matchesTenancy(d, query.tenancyId()))
                .filter(d -> query.objectType() == null
                        || d.objectType().getTypeKey().equals(query.objectType().getTypeKey()))
                .toList();
    }

    @Override
    @Transactional
    public void deregister(final Path path, final String tenancyId) {
        final RegistryKey key = new RegistryKey(path.value(), tenancyId);
        final AlphaDataSource<?> source = (AlphaDataSource<?>) sources.get(key);
        if (source == null) {
            return;
        }
        final DataSourceDescriptor descriptor = descriptorCache.get(key);

        DataSourceDescriptorEntity.PK pk = new DataSourceDescriptorEntity.PK(path.value(), tenancyId);
        DataSourceDescriptorEntity entity = entityManager.find(DataSourceDescriptorEntity.class, pk);
        if (entity != null) {
            entityManager.remove(entity);
        }

        source.markForRemoval(() -> {
            if (sources.remove(key, source)) {
                descriptorCache.remove(key);
            }
        });

        if (descriptor != null) {
            dataSourceDeregisteredEvent.fireAsync(new DataSourceDeregistered(descriptor, source))
                    .whenComplete((e, t) -> {
                        if (t != null) {
                            LOG.warnf(t, "DataSourceDeregistered observer failed for path %s", path);
                        }
                    });
        }
    }

    @Override
    @Transactional
    public void update(final DataSourceDescriptor descriptor) {
        final RegistryKey key = new RegistryKey(descriptor.path().value(), descriptor.tenancyId());
        final DataSourceDescriptor existing = descriptorCache.get(key);
        if (existing == null) {
            throw new IllegalStateException("No DataSource registered for path=" +
                    descriptor.path() + ", tenancyId=" + descriptor.tenancyId());
        }
        if (!descriptor.objectType().getTypeKey().equals(existing.objectType().getTypeKey())) {
            throw new IllegalArgumentException(
                    "objectType is immutable — deregister and re-register to change type");
        }

        DataSourceDescriptorEntity.PK pk =
                new DataSourceDescriptorEntity.PK(descriptor.path().value(), descriptor.tenancyId());
        DataSourceDescriptorEntity entity = entityManager.find(DataSourceDescriptorEntity.class, pk);
        if (entity != null) {
            entity.updateFrom(descriptor);
        } else {
            LOG.warnf("DataSource entity missing from DB for path=%s, tenancyId=%s — cache/DB divergence",
                    descriptor.path(), descriptor.tenancyId());
        }

        descriptorCache.put(key, descriptor);

        DataSource<?> ds = sources.get(key);
        if (ds == null) {
            LOG.debugf("DataSource deregistered during update for path=%s — skipping event",
                    descriptor.path());
            return;
        }
        dataSourceUpdatedEvent.fireAsync(new DataSourceUpdated(existing, descriptor, ds))
                .whenComplete((e, t) -> {
                    if (t != null) {
                        LOG.warnf(t, "DataSourceUpdated observer failed for path %s",
                                descriptor.path());
                    }
                });
    }

    private static boolean matchesTenancy(final DataSourceDescriptor d, final String tenancyId) {
        return d.tenancyId().equals(tenancyId)
                || d.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID);
    }
}
