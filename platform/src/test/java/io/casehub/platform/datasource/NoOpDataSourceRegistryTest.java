package io.casehub.platform.datasource;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceQuery;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class NoOpDataSourceRegistryTest {

    private final NoOpDataSourceRegistry registry = new NoOpDataSourceRegistry();

    @Test
    void register_returnsStub() {
        var desc = new DataSourceDescriptor(
                Path.parse("test"), "t1",
                new ClassObjectType<>(Object.class), null,
                Set.of(), Map.of(), Map.of());
        DataSource<?> dataSource = registry.register(desc);
        assertThat(dataSource).isNotNull();

        // Stub accepts add() calls (silent no-op)
        @SuppressWarnings("unchecked")
        DataSource<Object> typedDataSource = (DataSource<Object>) dataSource;
        typedDataSource.add(new Object());

        // Stub returns inert subscription handles
        var handle = typedDataSource.subscribe(obj -> {});
        assertThat(handle).isNotNull();
        assertThat(handle.isActive()).isFalse();
        handle.unsubscribe();  // no-op
    }

    @Test
    void resolve_alwaysEmpty() {
        assertThat(registry.resolve(Path.parse("any"), "t1")).isEmpty();
    }

    @Test
    void resolveSource_alwaysEmpty() {
        assertThat(registry.resolveSource(Path.parse("any"), "t1")).isEmpty();
    }

    @Test
    void discover_alwaysEmpty() {
        assertThat(registry.discover(new DataSourceQuery("t1", null))).isEmpty();
    }

    @Test
    void deregister_noOp() {
        registry.deregister(Path.parse("any"), "t1");
    }

    @Test
    void update_silentNoOp() {
        var desc = new DataSourceDescriptor(
                Path.of("test"), "t1", new ClassObjectType<>(Object.class), null,
                Set.of(), Map.of(), Map.of());
        registry.update(desc);
    }
}
