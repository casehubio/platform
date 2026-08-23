package io.casehub.platform.mcp;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DynamicToolRegistrarTest {

    @Inject
    ToolManager toolManager;
    @Inject
    DynamicToolRegistrar registrar;


    @Test
    void casehubActionToolIsRegistered() {
        var tool = toolManager.getTool("casehub_action");
        assertThat(tool).isNotNull();
        assertThat(tool.description()).contains("Execute a CaseHub operation");
    }

    @Test
    void casehubActionToolIsProgrammatic() {
        var tool = toolManager.getTool("casehub_action");
        assertThat(tool).isNotNull();
        assertThat(tool.isMethod()).isFalse();
    }

    @Test
    void casehubModelToolIsStillAnnotationBased() {
        var tool = toolManager.getTool("casehub_model");
        assertThat(tool).isNotNull();
        assertThat(tool.isMethod()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void casehubActionSchemaContainsDomainEnum() {
        var tool = toolManager.getTool("casehub_action");
        assertThat(tool).isNotNull();

        var json = tool.asJson();
        assertThat(json).isNotNull();
        var inputSchema = json.getJsonObject("inputSchema");
        assertThat(inputSchema).isNotNull();

        var properties = inputSchema.getJsonObject("properties");
        assertThat(properties).isNotNull();

        var domainProp = properties.getJsonObject("domain");
        assertThat(domainProp).isNotNull();
        assertThat(domainProp.getString("type")).isEqualTo("string");

        var domainEnum = domainProp.getJsonArray("enum");
        assertThat(domainEnum).isNotNull();
        List<String> domainNames = domainEnum.stream()
                .map(Object::toString)
                .toList();
        assertThat(domainNames).contains("test");
    }

    @Test
    void casehubActionSchemaHasOperationDescription() {
        var tool = toolManager.getTool("casehub_action");
        var json = tool.asJson();
        var inputSchema = json.getJsonObject("inputSchema");
        var properties = inputSchema.getJsonObject("properties");
        var operationProp = properties.getJsonObject("operation");

        String description = operationProp.getString("description");
        assertThat(description).contains("test:");
        assertThat(description).contains("Queries:");
        assertThat(description).contains("Mutations:");
    }

    @Test
    void casehubActivateToolIsRegistered() {
        var tool = toolManager.getTool("casehub_activate");
        assertThat(tool).isNotNull();
        assertThat(tool.description()).contains("Activate a domain");
    }

    @Test
    void activateDomain_shouldRegisterPerOperationTools() {
        registrar.activateDomain("test");
        assertThat(toolManager.getTool("test_echo")).isNotNull();
        assertThat(toolManager.getTool("test_hello")).isNotNull();
        assertThat(toolManager.getTool("test_store")).isNotNull();
        assertThat(toolManager.getTool("test_create")).isNotNull();
    }

    @Test
    void activateDomain_shouldBeIdempotent() {
        registrar.activateDomain("test");
        var second = registrar.activateDomain("test");
        assertThat(second.content().get(0).asText().text())
                .contains("already activated");
    }

    @Test
    void activateDomain_unknownDomain_shouldReturnError() {
        var response = registrar.activateDomain("nonexistent");
        assertThat(response.isError()).isTrue();
    }

    @Test
    void activateDomain_queryShouldHaveReadOnlyAndIdempotentHints() {
        registrar.activateDomain("test");
        var echoTool = toolManager.getTool("test_echo");
        assertThat(echoTool).isNotNull();
        var annotations = echoTool.annotations();
        assertThat(annotations).isPresent();
        assertThat(annotations.get().readOnlyHint()).isTrue();
        assertThat(annotations.get().idempotentHint()).isTrue();
        assertThat(annotations.get().destructiveHint()).isFalse();
    }

    @Test
    void activateDomain_mutationShouldNotBeIdempotentOrReadOnly() {
        registrar.activateDomain("test");
        var storeTool = toolManager.getTool("test_store");
        assertThat(storeTool).isNotNull();
        var annotations = storeTool.annotations();
        assertThat(annotations).isPresent();
        assertThat(annotations.get().readOnlyHint()).isFalse();
        assertThat(annotations.get().idempotentHint()).isFalse();
        assertThat(annotations.get().destructiveHint()).isFalse();
    }
}
