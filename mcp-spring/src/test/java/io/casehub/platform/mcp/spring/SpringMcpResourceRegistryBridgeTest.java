package io.casehub.platform.mcp.spring;

import io.casehub.platform.api.mcp.McpResourceContent;
import io.casehub.platform.api.mcp.McpResourceDescriptor;
import io.casehub.platform.api.mcp.McpResourceHandle;
import io.casehub.platform.api.mcp.McpResourceRegistered;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringMcpResourceRegistryBridgeTest {

    private McpSyncServer server;
    private ApplicationEventPublisher eventPublisher;
    private SpringMcpResourceRegistryBridge bridge;

    @BeforeEach
    void setUp() {
        server = mock(McpSyncServer.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        bridge = new SpringMcpResourceRegistryBridge(server, eventPublisher);
    }

    @Test
    void registerStaticResource_trackedLocally() {
        McpResourceHandle handle = bridge.newResource(McpResourceDescriptor.of(
                        "test-resource", "test://resource", "text/plain", "A test"))
                .handler(req -> McpResourceContent.of(req.uri(), "hello", "text/plain"))
                .register();

        assertThat(handle).isNotNull();
        assertThat(bridge.resolve("test-resource")).isPresent();
        assertThat(bridge.list()).hasSize(1);
        verify(server).addResource(any(McpServerFeatures.SyncResourceSpecification.class));
        verify(eventPublisher).publishEvent(any(McpResourceRegistered.class));
    }

    @Test
    void deregisterResource_removedFromTracking() {
        bridge.newResource(McpResourceDescriptor.of(
                        "test-resource", "test://resource", "text/plain", "A test"))
                .handler(req -> McpResourceContent.of(req.uri(), "hello", "text/plain"))
                .register();

        bridge.deregister("test-resource");

        assertThat(bridge.resolve("test-resource")).isEmpty();
        assertThat(bridge.list()).isEmpty();
        verify(server).removeResource("test://resource");
    }

    @Test
    void deregisterViaHandle_removedFromTracking() {
        McpResourceHandle handle = bridge.newResource(McpResourceDescriptor.of(
                        "test-resource", "test://resource", "text/plain", "A test"))
                .handler(req -> McpResourceContent.of(req.uri(), "hello", "text/plain"))
                .register();

        handle.deregister();

        assertThat(bridge.resolve("test-resource")).isEmpty();
        assertThat(bridge.list()).isEmpty();
    }

    @Test
    void registerTemplate_trackedLocally() {
        McpResourceHandle handle = bridge.newResource(McpResourceDescriptor.template(
                        "test-template", "test://items/{id}", "application/json", "Item detail"))
                .handler(req -> McpResourceContent.of(req.uri(), "{}", "application/json"))
                .completion("id", () -> List.of("a", "b", "c"))
                .register();

        assertThat(handle).isNotNull();
        assertThat(bridge.resolve("test-template")).isPresent();
        assertThat(bridge.list()).hasSize(1);
        verify(server).addResourceTemplate(any(McpServerFeatures.SyncResourceTemplateSpecification.class));
    }

    @Test
    void deregisterTemplate_removedFromTracking() {
        bridge.newResource(McpResourceDescriptor.template(
                        "test-template", "test://items/{id}", "application/json", "Item detail"))
                .handler(req -> McpResourceContent.of(req.uri(), "{}", "application/json"))
                .register();

        bridge.deregister("test-template");

        assertThat(bridge.resolve("test-template")).isEmpty();
        verify(server).removeResourceTemplate("test://items/{id}");
    }

    @Test
    void registerWithoutHandler_throws() {
        var reg = bridge.newResource(McpResourceDescriptor.of(
                "test-resource", "test://resource", "text/plain", "test"));

        assertThatThrownBy(reg::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("handler is required");
    }

    @Test
    void subscribableTemplate_throws() {
        var reg = bridge.newResource(McpResourceDescriptor.template(
                        "test-template", "test://items/{id}", "application/json", "test")
                        .withSubscribable(true))
                .handler(req -> McpResourceContent.of(req.uri(), "{}", "application/json"));

        assertThatThrownBy(reg::register)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscribable");
    }

    @Test
    void resolveNonexistent_returnsEmpty() {
        assertThat(bridge.resolve("nonexistent")).isEmpty();
    }

    @Test
    void listEmpty_returnsEmptyList() {
        assertThat(bridge.list()).isEmpty();
    }

    @Test
    void doubleDeregisterViaHandle_idempotent() {
        McpResourceHandle handle = bridge.newResource(McpResourceDescriptor.of(
                        "test-resource", "test://resource", "text/plain", "A test"))
                .handler(req -> McpResourceContent.of(req.uri(), "hello", "text/plain"))
                .register();

        handle.deregister();
        handle.deregister();

        assertThat(bridge.list()).isEmpty();
    }
}
