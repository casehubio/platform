package io.casehub.platform.api.datasource;

import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceRegisteredTest {

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new DataSourceRegistered(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void holdsDescriptor() {
        var desc = new DataSourceDescriptor(
                Path.parse("test"), "t1",
                new ClassObjectType<>(Object.class), null,
                Set.of(), Map.of());
        var event = new DataSourceRegistered(desc);
        assertThat(event.descriptor()).isSameAs(desc);
    }
}
