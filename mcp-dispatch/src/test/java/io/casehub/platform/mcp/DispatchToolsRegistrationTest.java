package io.casehub.platform.mcp;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DispatchToolsRegistrationTest {

    @Inject
    ToolManager toolManager;

    @Test
    void casehubModelAndSearchAreAnnotationBasedTools() {
        var model = toolManager.getTool("casehub_model");
        assertThat(model).isNotNull();
        assertThat(model.isMethod()).isTrue();

        var search = toolManager.getTool("casehub_search");
        assertThat(search).isNotNull();
        assertThat(search.isMethod()).isTrue();
    }

    @Test
    void programmaticDispatchToolStillRegisteredByInfrastructure() {
        assertThat(toolManager.getTool("casehub_action")).isNotNull();
    }
}
