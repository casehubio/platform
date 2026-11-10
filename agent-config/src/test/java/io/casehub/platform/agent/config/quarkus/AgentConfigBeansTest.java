package io.casehub.platform.agent.config.quarkus;

import io.casehub.platform.agent.config.AgentManifestResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AgentConfigBeansTest {

    @Inject
    AgentManifestResult agentManifestResult;

    @Test
    void manifestResultProducedAtStartup() {
        assertThat(agentManifestResult).isNotNull();
        assertThat(agentManifestResult.defaultBackendKey()).isEqualTo("claude");
    }

    @Test
    void aliasesLoadedFromConfig() {
        assertThat(agentManifestResult.aliases()).containsKey("fast");
        var fastChain = agentManifestResult.aliases().get("fast");
        assertThat(fastChain.entries()).isNotEmpty();
    }
}
