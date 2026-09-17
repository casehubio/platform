package io.casehub.platform.datasource.spring.jpa;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceQuery;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.datasource.jpa.DataSourceDescriptorEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringDataSourceRegistryTest.TestConfig.class)
class SpringDataSourceRegistryTest {

    private static final String TENANT = "test-tenant";

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = DataSourceDescriptorEntityRepository.class)
    @EntityScan(basePackageClasses = DataSourceDescriptorEntity.class)
    static class TestConfig {
        @Bean
        SpringDataSourceRegistry springDataSourceRegistry(
                DataSourceDescriptorEntityRepository repo,
                ApplicationEventPublisher events) {
            return new SpringDataSourceRegistry(repo, events);
        }
    }

    @Autowired SpringDataSourceRegistry registry;

    private DataSourceDescriptor testDescriptor(String pathValue) {
        return new DataSourceDescriptor(
                Path.parse(pathValue),
                TENANT,
                new ClassObjectType<>(String.class),
                null,
                Set.of(),
                Map.of(),
                Map.of());
    }

    @Test
    void registerAndResolve() {
        DataSourceDescriptor descriptor = testDescriptor("test/events");
        DataSource<?> ds = registry.register(descriptor);

        assertNotNull(ds);
        assertTrue(registry.resolve(Path.parse("test/events"), TENANT).isPresent());
        assertTrue(registry.resolveSource(Path.parse("test/events"), TENANT).isPresent());
    }

    @Test
    void registerIsIdempotent() {
        DataSourceDescriptor descriptor = testDescriptor("test/events");
        DataSource<?> ds1 = registry.register(descriptor);
        DataSource<?> ds2 = registry.register(descriptor);

        assertSame(ds1, ds2);
    }

    @Test
    void deregister() {
        registry.register(testDescriptor("test/events"));
        registry.deregister(Path.parse("test/events"), TENANT);

        assertTrue(registry.resolve(Path.parse("test/events"), TENANT).isEmpty());
    }

    @Test
    void discover() {
        registry.register(testDescriptor("discover/a"));
        registry.register(testDescriptor("discover/b"));

        List<DataSourceDescriptor> results = registry.discover(
                new DataSourceQuery(TENANT, null));
        long discoverCount = results.stream()
                .filter(d -> d.path().value().startsWith("discover/"))
                .count();
        assertEquals(2, discoverCount);
    }

    @Test
    void update() {
        registry.register(testDescriptor("test/events"));

        DataSourceDescriptor updated = new DataSourceDescriptor(
                Path.parse("test/events"),
                TENANT,
                new ClassObjectType<>(String.class),
                null,
                Set.of("custom.event"),
                Map.of("key", "value"),
                Map.of());

        registry.update(updated);

        DataSourceDescriptor resolved = registry.resolve(Path.parse("test/events"), TENANT).orElseThrow();
        assertEquals(Set.of("custom.event"), resolved.acceptedEventTypes());
        assertEquals("value", resolved.properties().get("key"));
    }

    @Test
    void updateFailsOnMissingKey() {
        assertThrows(IllegalStateException.class, () ->
                registry.update(testDescriptor("nonexistent")));
    }

    @Test
    void resolveNotFound() {
        assertTrue(registry.resolve(Path.parse("nonexistent"), TENANT).isEmpty());
    }
}
