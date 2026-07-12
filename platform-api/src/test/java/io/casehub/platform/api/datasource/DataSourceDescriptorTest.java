package io.casehub.platform.api.datasource;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceDescriptorTest {

    @Test
    void immutableCopies() {
        var mutableProps = new java.util.HashMap<String, String>();
        mutableProps.put("key", "value");
        var mutableTypes = new java.util.HashSet<String>();
        mutableTypes.add("io.casehub.siem.alert");

        DataSourceDescriptor desc = new DataSourceDescriptor(
                Path.parse("siem/alerts"), "tenant-1",
                new ClassObjectType<>(String.class), null,
                mutableTypes, mutableProps, Map.of());

        mutableProps.put("other", "val");
        mutableTypes.add("io.casehub.other");
        assertThat(desc.properties()).hasSize(1);
        assertThat(desc.acceptedEventTypes()).hasSize(1);
    }

    @Test
    void isPlatformGlobal() {
        DataSourceDescriptor global = new DataSourceDescriptor(
                Path.parse("global"), TenancyConstants.PLATFORM_TENANT_ID,
                new ClassObjectType<>(Object.class), null,
                Set.of(), Map.of(), Map.of());
        DataSourceDescriptor tenant = new DataSourceDescriptor(
                Path.parse("tenant"), "t1",
                new ClassObjectType<>(Object.class), null,
                Set.of(), Map.of(), Map.of());
        assertThat(global.isPlatformGlobal()).isTrue();
        assertThat(tenant.isPlatformGlobal()).isFalse();
    }

    @Test
    void nullsRejected() {
        assertThatThrownBy(() -> new DataSourceDescriptor(
                null, "t", new ClassObjectType<>(Object.class), null, Set.of(), Map.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
