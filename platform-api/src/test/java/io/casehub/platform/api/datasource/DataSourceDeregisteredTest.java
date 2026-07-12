package io.casehub.platform.api.datasource;

import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceDeregisteredTest {

    private final DataSourceDescriptor desc = new DataSourceDescriptor(
            Path.parse("test"), "t1",
            new ClassObjectType<>(Object.class), null,
            Set.of(), Map.of());

    private final DataSource<Object> stubDs = new DataSource<>() {
        @Override public void add(Object value) {}
        @Override public SubscriptionHandle subscribe(DataProcessor<? super Object> p) { return null; }
        @Override public <U> SubscriptionHandle subscribe(ObjectType<U> t, DataProcessor<? super U> p) { return null; }
        @Override public <U> SubscriptionHandle subscribe(ObjectType<U> t, Predicate<U> f, DataProcessor<? super U> p) { return null; }
        @Override public <U> SubscriptionHandle subscribe(Class<U> t, Predicate<U> f, DataProcessor<? super U> p) { return null; }
    };

    @Test
    void rejectsNullDescriptor() {
        assertThatThrownBy(() -> new DataSourceDeregistered(null, stubDs))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullDataSource() {
        assertThatThrownBy(() -> new DataSourceDeregistered(desc, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void holdsBothFields() {
        var event = new DataSourceDeregistered(desc, stubDs);
        assertThat(event.descriptor()).isSameAs(desc);
        assertThat(event.dataSource()).isSameAs(stubDs);
    }
}
