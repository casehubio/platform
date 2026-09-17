package io.casehub.platform.datasource.spring.jpa;

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
import io.casehub.platform.datasource.jpa.DataSourceDescriptorEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SpringDataSourceRegistry implements DataSourceRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SpringDataSourceRegistry.class);

    private final ConcurrentHashMap<CacheKey, DataSourceDescriptor> descriptorCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey, DataSource<?>> sources = new ConcurrentHashMap<>();

    private final DataSourceDescriptorEntityRepository repo;
    private final ApplicationEventPublisher events;

    public SpringDataSourceRegistry(DataSourceDescriptorEntityRepository repo,
                                    ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<DataSourceDescriptorEntity> entities = repo.findAll();
        for (DataSourceDescriptorEntity entity : entities) {
            DataSourceDescriptor descriptor = entity.toDomain();
            CacheKey key = new CacheKey(descriptor.path().value(), descriptor.tenancyId());
            descriptorCache.put(key, descriptor);
            sources.put(key, new AlphaDataSource<>());
            events.publishEvent(new DataSourceRegistered(descriptor));
        }
        LOG.info("DataSource JPA registry started — {} descriptor(s) reconciled", entities.size());
    }

    @Override
    @Transactional
    public DataSource<?> register(DataSourceDescriptor descriptor) {
        CacheKey key = new CacheKey(descriptor.path().value(), descriptor.tenancyId());

        DataSource<?> existing = sources.get(key);
        if (existing instanceof AlphaDataSource<?> alpha && !alpha.isPendingRemoval()) {
            return existing;
        }

        DataSourceDescriptorEntity entity = DataSourceDescriptorEntity.fromDomain(descriptor);
        repo.saveAndFlush(entity);

        AlphaDataSource<?> ds = new AlphaDataSource<>();
        sources.put(key, ds);
        descriptorCache.put(key, descriptor);

        events.publishEvent(new DataSourceRegistered(descriptor));
        return ds;
    }

    @Override
    public Optional<DataSourceDescriptor> resolve(Path path, String tenancyId) {
        DataSourceDescriptor tenant = descriptorCache.get(new CacheKey(path.value(), tenancyId));
        if (tenant != null) return Optional.of(tenant);
        DataSourceDescriptor global = descriptorCache.get(
                new CacheKey(path.value(), TenancyConstants.PLATFORM_TENANT_ID));
        return Optional.ofNullable(global);
    }

    @Override
    public Optional<DataSource<?>> resolveSource(Path path, String tenancyId) {
        DataSource<?> tenant = sources.get(new CacheKey(path.value(), tenancyId));
        if (tenant != null) return Optional.of(tenant);
        DataSource<?> global = sources.get(
                new CacheKey(path.value(), TenancyConstants.PLATFORM_TENANT_ID));
        return Optional.ofNullable(global);
    }

    @Override
    public List<DataSourceDescriptor> discover(DataSourceQuery query) {
        return descriptorCache.values().stream()
                .filter(d -> matchesTenancy(d, query.tenancyId()))
                .filter(d -> query.objectType() == null
                        || d.objectType().getTypeKey().equals(query.objectType().getTypeKey()))
                .toList();
    }

    @Override
    @Transactional
    public void deregister(Path path, String tenancyId) {
        CacheKey key = new CacheKey(path.value(), tenancyId);
        AlphaDataSource<?> source = (AlphaDataSource<?>) sources.get(key);
        if (source == null) {
            return;
        }
        DataSourceDescriptor descriptor = descriptorCache.get(key);

        repo.findByPathAndTenancyId(path.value(), tenancyId).ifPresent(repo::delete);

        source.markForRemoval(() -> {
            if (sources.remove(key, source)) {
                descriptorCache.remove(key);
            }
        });

        if (descriptor != null) {
            events.publishEvent(new DataSourceDeregistered(descriptor, source));
        }
    }

    @Override
    @Transactional
    public void update(DataSourceDescriptor descriptor) {
        CacheKey key = new CacheKey(descriptor.path().value(), descriptor.tenancyId());
        DataSourceDescriptor existing = descriptorCache.get(key);
        if (existing == null) {
            throw new IllegalStateException("No DataSource registered for path=" +
                    descriptor.path() + ", tenancyId=" + descriptor.tenancyId());
        }
        if (!descriptor.objectType().getTypeKey().equals(existing.objectType().getTypeKey())) {
            throw new IllegalArgumentException(
                    "objectType is immutable — deregister and re-register to change type");
        }

        repo.findByPathAndTenancyId(descriptor.path().value(), descriptor.tenancyId())
                .ifPresent(entity -> entity.updateFrom(descriptor));

        descriptorCache.put(key, descriptor);

        DataSource<?> ds = sources.get(key);
        if (ds != null) {
            events.publishEvent(new DataSourceUpdated(existing, descriptor, ds));
        }
    }

    private static boolean matchesTenancy(DataSourceDescriptor d, String tenancyId) {
        return d.tenancyId().equals(tenancyId)
                || d.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID);
    }

    private record CacheKey(String path, String tenancyId) {}
}
