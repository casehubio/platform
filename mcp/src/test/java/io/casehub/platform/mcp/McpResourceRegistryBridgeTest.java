package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpResourceContent;
import io.casehub.platform.api.mcp.McpResourceDescriptor;
import io.casehub.platform.api.mcp.McpResourceRegistry;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

@QuarkusTest
class McpResourceRegistryBridgeTest {

    @Inject
    McpResourceRegistry registry;

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Test
    void staticResourceRegisteredWithResourceManager() {
        var handle = registry.newResource(McpResourceDescriptor.of(
                        "test-static", "test://static", "text/plain", "Test static"))
                .handler(req -> McpResourceContent.of(req.uri(), "hello"))
                .register();

        assertThat(handle).isNotNull();
        var resource = resourceManager.getResource("test://static");
        assertThat(resource).isNotNull();
        assertThat(resource.uri()).isEqualTo("test://static");

        handle.deregister();
    }

    @Test
    void templateResourceRegisteredWithTemplateManager() {
        var handle = registry.newResource(McpResourceDescriptor.template(
                        "test-template", "test://items/{id}", "application/json", "Test template"))
                .handler(req -> McpResourceContent.of(req.uri(),
                        "{\"id\":\"" + req.templateArgs().get("id") + "\"}"))
                .register();

        assertThat(handle).isNotNull();
        var template = resourceTemplateManager.getResourceTemplate("test-template");
        assertThat(template).isNotNull();
        assertThat(template.uriTemplate()).isEqualTo("test://items/{id}");

        handle.deregister();
    }

    @Test
    void registryResolveAndList() {
        var handle = registry.newResource(McpResourceDescriptor.of(
                        "test-resolve", "test://resolve", null, "Resolve test"))
                .handler(req -> McpResourceContent.of(req.uri(), "data"))
                .register();

        assertThat(registry.resolve("test-resolve")).isPresent();
        assertThat(registry.list()).anyMatch(d -> d.name().equals("test-resolve"));

        handle.deregister();
        assertThat(registry.resolve("test-resolve")).isEmpty();
    }

    @Test
    void registerWithoutHandlerThrows() {
        assertThatIllegalStateException().isThrownBy(() ->
                registry.newResource(McpResourceDescriptor.of(
                                "no-handler", "test://no-handler", null, "Missing handler"))
                        .register());
    }

    @Test
    void subscribableTemplateRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                registry.newResource(McpResourceDescriptor.template(
                                "bad-tpl", "test://t/{x}", null, "Bad").withSubscribable(true))
                        .handler(req -> McpResourceContent.of(req.uri(), ""))
                        .register());
    }

    @Test
    void deregisterByNameInvalidatesHandle() {
        var handle = registry.newResource(McpResourceDescriptor.of(
                        "test-dereg", "test://dereg", null, "Deregister test"))
                .handler(req -> McpResourceContent.of(req.uri(), "data"))
                .register();

        registry.deregister("test-dereg");
        assertThat(registry.resolve("test-dereg")).isEmpty();
        assertThatNoException().isThrownBy(() -> handle.notifyUpdate("test://dereg"));
        assertThatNoException().isThrownBy(handle::deregister);
    }

    @Test
    void doubleDeregisterIsIdempotent() {
        var handle = registry.newResource(McpResourceDescriptor.of(
                        "test-double-dereg", "test://double-dereg", null, "Double deregister"))
                .handler(req -> McpResourceContent.of(req.uri(), "data"))
                .register();

        handle.deregister();
        assertThatNoException().isThrownBy(handle::deregister);
    }

    @Test
    void deregisterUnknownNameIsNoOp() {
        assertThatNoException().isThrownBy(() -> registry.deregister("nonexistent"));
    }

    @Test
    void bridgeInstanceBeatsNoOp() {
        assertThat(registry).isInstanceOf(McpResourceRegistryBridge.class);
    }
}
