package io.casehub.platform.datasource.jpa;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceQuery;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@Transactional
class JpaDataSourceRegistryTest {

    @Inject
    JpaDataSourceRegistry registry;

    private DataSourceDescriptor descriptor(String path, String tenancyId) {
        return new DataSourceDescriptor(
                Path.parse(path), tenancyId,
                new ClassObjectType<>(Object.class), null,
                Set.of(), Map.of(), Map.of());
    }

    @Test
    void register_returnsDataSource() {
        DataSource<?> ds = registry.register(descriptor("jpa-test-1", "t1"));
        assertThat(ds).isNotNull();
    }

    @Test
    void register_idempotent_returnsSameDataSource() {
        DataSource<?> ds1 = registry.register(descriptor("jpa-test-2", "t1"));
        DataSource<?> ds2 = registry.register(descriptor("jpa-test-2", "t1"));
        assertThat(ds2).isSameAs(ds1);
    }

    @Test
    void resolve_tenantSpecific() {
        registry.register(descriptor("jpa-test-3", "t1"));
        assertThat(registry.resolve(Path.parse("jpa-test-3"), "t1")).isPresent();
        assertThat(registry.resolve(Path.parse("jpa-test-3"), "t2")).isEmpty();
    }

    @Test
    void resolve_platformGlobalFallback() {
        registry.register(descriptor("jpa-global-1", TenancyConstants.PLATFORM_TENANT_ID));
        assertThat(registry.resolve(Path.parse("jpa-global-1"), "any-tenant")).isPresent();
    }

    @Test
    void resolveSource_returnsDataSource() {
        registry.register(descriptor("jpa-test-4", "t1"));
        assertThat(registry.resolveSource(Path.parse("jpa-test-4"), "t1")).isPresent();
    }

    @Test
    void discover_includesPlatformGlobal() {
        registry.register(descriptor("jpa-disc-a", "t1"));
        registry.register(descriptor("jpa-disc-b", TenancyConstants.PLATFORM_TENANT_ID));
        var results = registry.discover(new DataSourceQuery("t1", null));
        assertThat(results.stream().map(d -> d.path().value()))
                .contains("jpa-disc-a", "jpa-disc-b");
    }

    @Test
    void deregister_removesBoth() {
        registry.register(descriptor("jpa-test-5", "t1"));
        registry.deregister(Path.parse("jpa-test-5"), "t1");
        assertThat(registry.resolve(Path.parse("jpa-test-5"), "t1")).isEmpty();
        assertThat(registry.resolveSource(Path.parse("jpa-test-5"), "t1")).isEmpty();
    }

    @Test
    void deregister_unknownKey_noOp() {
        registry.deregister(Path.parse("nonexistent"), "t1");
    }

    @Test
    void update_replacesDescriptor() {
        registry.register(descriptor("jpa-test-6", "t1"));
        var updated = new DataSourceDescriptor(
                Path.parse("jpa-test-6"), "t1", new ClassObjectType<>(Object.class),
                Path.parse("new-ep"), Set.of("order.created"), Map.of("k", "v"), Map.of());
        registry.update(updated);
        assertThat(registry.resolve(Path.parse("jpa-test-6"), "t1")).contains(updated);
    }

    @Test
    void update_preservesDataSourceInstance() {
        DataSource<?> ds = registry.register(descriptor("jpa-test-7", "t1"));
        var updated = new DataSourceDescriptor(
                Path.parse("jpa-test-7"), "t1", new ClassObjectType<>(Object.class),
                Path.parse("new-ep"), Set.of(), Map.of(), Map.of());
        registry.update(updated);
        assertThat(registry.resolveSource(Path.parse("jpa-test-7"), "t1")).containsSame(ds);
    }

    @Test
    void update_throwsIfNotFound() {
        assertThatThrownBy(() -> registry.update(descriptor("nonexistent", "t1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void update_throwsIfObjectTypeChanges() {
        registry.register(descriptor("jpa-test-8", "t1"));
        var changed = new DataSourceDescriptor(
                Path.parse("jpa-test-8"), "t1", new ClassObjectType<>(String.class),
                null, Set.of(), Map.of(), Map.of());
        assertThatThrownBy(() -> registry.update(changed))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
