package io.casehub.platform.datasource.memory;

import io.casehub.platform.api.datasource.*;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDataSourceRegistryTest {

    private final InMemoryDataSourceRegistry registry = new InMemoryDataSourceRegistry();

    private DataSourceDescriptor descriptor(String path, String tenancyId) {
        return new DataSourceDescriptor(
                Path.parse(path), tenancyId,
                new ClassObjectType<>(Object.class), null,
                Set.of(), Map.of());
    }

    @Test
    void register_returnsDataSource() {
        DataSource<?> ds = registry.register(descriptor("test", "t1"));
        assertThat(ds).isNotNull();
    }

    @Test
    void resolve_tenantSpecific() {
        registry.register(descriptor("test", "t1"));
        assertThat(registry.resolve(Path.parse("test"), "t1")).isPresent();
        assertThat(registry.resolve(Path.parse("test"), "t2")).isEmpty();
    }

    @Test
    void resolve_platformGlobalFallback() {
        registry.register(descriptor("global", TenancyConstants.PLATFORM_TENANT_ID));
        assertThat(registry.resolve(Path.parse("global"), "any-tenant")).isPresent();
    }

    @Test
    void resolve_tenantOverridesPlatform() {
        registry.register(descriptor("path", TenancyConstants.PLATFORM_TENANT_ID));
        registry.register(descriptor("path", "t1"));
        var result = registry.resolve(Path.parse("path"), "t1");
        assertThat(result).isPresent();
        assertThat(result.get().tenancyId()).isEqualTo("t1");
    }

    @Test
    void resolveSource_returnsRuntime() {
        registry.register(descriptor("test", "t1"));
        assertThat(registry.resolveSource(Path.parse("test"), "t1")).isPresent();
    }

    @Test
    void discover_includesPlatformGlobal() {
        registry.register(descriptor("a", "t1"));
        registry.register(descriptor("b", TenancyConstants.PLATFORM_TENANT_ID));
        var results = registry.discover(new DataSourceQuery("t1", null));
        assertThat(results).hasSize(2);
    }

    @Test
    void discover_excludesCrossTenant() {
        registry.register(descriptor("a", "t1"));
        registry.register(descriptor("b", "t2"));
        var results = registry.discover(new DataSourceQuery("t1", null));
        assertThat(results).hasSize(1);
    }

    @Test
    void deregister_removesBoth() {
        registry.register(descriptor("test", "t1"));
        registry.deregister(Path.parse("test"), "t1");
        assertThat(registry.resolve(Path.parse("test"), "t1")).isEmpty();
        assertThat(registry.resolveSource(Path.parse("test"), "t1")).isEmpty();
    }
}
