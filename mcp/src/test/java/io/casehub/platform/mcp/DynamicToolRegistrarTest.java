package io.casehub.platform.mcp;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DynamicToolRegistrarTest {

    @Inject
    ToolManager toolManager;

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
        assertThat(domainEnum.getString(0)).isEqualTo("test");
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
}
