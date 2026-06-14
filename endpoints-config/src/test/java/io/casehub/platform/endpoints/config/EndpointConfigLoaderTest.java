package io.casehub.platform.endpoints.config;

import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.endpoints.memory.InMemoryEndpointRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@QuarkusTest
class EndpointConfigLoaderTest {

    @Inject
    EndpointRegistry registry;

    @Test
    void test1_yaml_endpoints_registered_and_cdi_wired() {
        // assertInstanceOf works because the CDI proxy IS-A InMemoryEndpointRegistry (subclass)
        // Do NOT use registry.getClass().getSimpleName() — that returns the proxy class name
        assertInstanceOf(InMemoryEndpointRegistry.class, registry);

        Optional<EndpointDescriptor> result = registry.resolve(
            Path.of("casehubio", "workers", "crm"), "tenant-b");
        assertThat(result).isPresent();
        assertThat(result.get().protocol()).isEqualTo(EndpointProtocol.HTTP);
        assertThat(result.get().properties().get("url")).isEqualTo("https://crm.example.com");
    }

    @Test
    void test2_platform_global_endpoint_visible_to_any_tenant() {
        Optional<EndpointDescriptor> result = registry.resolve(
            Path.of("casehubio", "platform", "health"), "tenant-a");
        assertThat(result).isPresent();
        assertThat(result.get().tenancyId()).isEqualTo(TenancyConstants.PLATFORM_TENANT_ID);
        assertThat(result.get().properties().get("url"))
            .isEqualTo("https://health.casehub.io/check");
    }

    @Test
    void test3_tenant_b_endpoint_invisible_to_tenant_a() {
        Optional<EndpointDescriptor> result = registry.resolve(
            Path.of("casehubio", "workers", "crm"), "tenant-a");
        assertThat(result).isEmpty();
    }

    @Test
    void test4_later_file_replaces_earlier_for_same_path_and_tenancy() {
        // external/salesforce/prod for tenant-a: test-endpoints.yaml has v1 URL,
        // test-endpoints-override.yaml (loaded last) has v2 URL — v2 must win
        Optional<EndpointDescriptor> result = registry.resolve(
            Path.of("external", "salesforce", "prod"), "tenant-a");
        assertThat(result).isPresent();
        assertThat(result.get().properties().get("url"))
            .isEqualTo("https://salesforce-v2.example.com/api");
    }
}
