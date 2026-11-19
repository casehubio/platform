package io.casehub.platform.mcp;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(DynamicToolRegistrarFlatModeTest.FlatProfile.class)
class DynamicToolRegistrarFlatModeTest {

    public static class FlatProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("casehub.mcp.tool-exposure", "flat");
        }
    }

    @Inject
    ToolManager toolManager;

    @Test
    void flatModeDoesNotRegisterCasehubAction() {
        assertThat(toolManager.getTool("casehub_action")).isNull();
    }

    @Test
    void flatModeDoesNotRegisterCasehubActivate() {
        assertThat(toolManager.getTool("casehub_activate")).isNull();
    }

    @Test
    void flatModeRegistersIndividualDomainTools() {
        // The "test" domain has operations: echo, hello, store, create
        assertThat(toolManager.getTool("test_echo")).isNotNull();
        assertThat(toolManager.getTool("test_hello")).isNotNull();
        assertThat(toolManager.getTool("test_store")).isNotNull();
        assertThat(toolManager.getTool("test_create")).isNotNull();
    }

    @Test
    void flatModeStillRegistersReportTools() {
        assertThat(toolManager.getTool("test_report")).isNotNull();
    }
}
