package io.casehub.platform.datasource.memory;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceRegistered;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.datasource.DataSourceRouter;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRouterTest {

    private InMemoryDataSourceRegistry registry;
    private DataSourceRouter router;

    @BeforeEach
    void setUp() {
        registry = new InMemoryDataSourceRegistry();
        router = new DataSourceRouter(registry);
    }

    private CloudEvent cloudEvent(String type, String tenancyId) {
        var builder = CloudEventBuilder.v1()
                .withId("test-1")
                .withSource(URI.create("/test"))
                .withType(type);
        if (tenancyId != null) {
            builder.withExtension("tenancyid", tenancyId);
        }
        return builder.build();
    }

    @Test
    void routesToMatchingTenantDataSource() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("siem"), "t1",
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of(), Map.of());
        DataSource<?> ds = registry.register(descriptor);
        List<Object> received = new ArrayList<>();
        ds.subscribe(received::add);

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));
        router.onCloudEvent(cloudEvent("siem.alert", "t1"));

        assertThat(received).hasSize(1);
    }

    @Test
    void doesNotRouteToWrongTenant() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("siem"), "t1",
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of(), Map.of());
        DataSource<?> ds = registry.register(descriptor);
        List<Object> received = new ArrayList<>();
        ds.subscribe(received::add);

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));
        router.onCloudEvent(cloudEvent("siem.alert", "t2"));

        assertThat(received).isEmpty();
    }

    @Test
    void routesToPlatformGlobal() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("global"), io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID,
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of(), Map.of());
        DataSource<?> ds = registry.register(descriptor);
        List<Object> received = new ArrayList<>();
        ds.subscribe(received::add);

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));
        router.onCloudEvent(cloudEvent("any.type", "any-tenant"));

        assertThat(received).hasSize(1);
    }

    @Test
    void acceptedEventTypes_filtersBeforeRouting() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("siem"), "t1",
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of("siem.alert.critical"),
                Map.of());
        DataSource<?> ds = registry.register(descriptor);
        List<Object> received = new ArrayList<>();
        ds.subscribe(received::add);

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));
        router.onCloudEvent(cloudEvent("siem.alert.info", "t1"));
        router.onCloudEvent(cloudEvent("siem.alert.critical", "t1"));

        assertThat(received).hasSize(1);
    }
}
