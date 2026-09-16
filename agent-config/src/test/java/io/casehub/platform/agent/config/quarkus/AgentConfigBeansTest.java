package io.casehub.platform.agent.config.quarkus;

import io.casehub.platform.agent.config.ManifestResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AgentConfigBeansTest {

    @Inject
    ManifestResult manifestResult;

    @Test
    void manifestResultProducedAtStartup() {
        assertThat(manifestResult).isNotNull();
        assertThat(manifestResult.defaultBackendKey()).isEqualTo("claude");
    }

    @Test
    void aliasesLoadedFromConfig() {
        assertThat(manifestResult.aliases()).containsKey("fast");
        var fastQuery = manifestResult.aliases().get("fast");
        assertThat(fastQuery.tier()).isNotNull();
    }
}
