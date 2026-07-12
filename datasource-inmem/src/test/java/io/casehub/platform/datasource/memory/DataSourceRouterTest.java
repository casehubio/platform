package io.casehub.platform.datasource.memory;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceDeregistered;
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

    // --- Deregistration ---

    @Test
    void deregister_removesWiredRoute() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("siem"), "t1",
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of(), Map.of());
        DataSource<?> ds = registry.register(descriptor);
        List<Object> received = new ArrayList<>();
        ds.subscribe(received::add);

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));
        router.onDataSourceDeregistered(new DataSourceDeregistered(descriptor, ds));
        router.onCloudEvent(cloudEvent("siem.alert", "t1"));

        assertThat(received).isEmpty();
    }

    @Test
    void deregister_identityMismatch_doesNotRemove() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("siem"), "t1",
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of(), Map.of());
        DataSource<?> ds = registry.register(descriptor);
        List<Object> received = new ArrayList<>();
        ds.subscribe(received::add);

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));

        DataSource<Object> otherDs = new AlphaDataSource<>();
        router.onDataSourceDeregistered(new DataSourceDeregistered(descriptor, otherDs));
        router.onCloudEvent(cloudEvent("siem.alert", "t1"));

        assertThat(received).hasSize(1);
    }

    @Test
    void wireRoute_replacesWhenInstanceChanges() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("siem"), "t1",
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of(), Map.of());

        DataSource<?> ds1 = registry.register(descriptor);
        List<Object> received1 = new ArrayList<>();
        ds1.subscribe(received1::add);

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));

        @SuppressWarnings("unchecked")
        var handle = ((DataSource<Object>) ds1).subscribe(obj -> {});
        registry.deregister(Path.parse("siem"), "t1");
        handle.unsubscribe();

        DataSource<?> ds2 = registry.register(descriptor);
        List<Object> received2 = new ArrayList<>();
        ds2.subscribe(received2::add);

        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));
        router.onCloudEvent(cloudEvent("siem.alert", "t1"));

        assertThat(received1).isEmpty();
        assertThat(received2).hasSize(1);
    }

    @Test
    void wireRoute_skipsWhenResolveReturnsEmpty() {
        DataSourceDescriptor descriptor = new DataSourceDescriptor(
                Path.parse("siem"), "t1",
                new ClassObjectType<>(CloudEvent.class), null,
                Set.of(), Map.of());
        registry.register(descriptor);
        registry.deregister(Path.parse("siem"), "t1");

        router.onStartup(null);
        router.onDataSourceRegistered(new DataSourceRegistered(descriptor));
        router.onCloudEvent(cloudEvent("siem.alert", "t1"));
    }
}
