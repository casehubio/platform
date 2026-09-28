package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.ComponentStatus;
import io.casehub.platform.api.mcp.DomainReport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformLandscapeTest {

    @Test
    void aggregatesDomains() {
        var work = new DomainReport("work", ComponentStatus.AVAILABLE, Map.of(), Map.of());
        var connectors = new DomainReport("connectors", ComponentStatus.DEGRADED, Map.of(), Map.of());

        var landscape = new PlatformLandscape(
                Map.of("work", work, "connectors", connectors),
                Map.of("simulation", Map.of("active", false)));

        assertThat(landscape.domains()).hasSize(2);
        assertThat(landscape.domains().get("work").status()).isEqualTo(ComponentStatus.AVAILABLE);
        assertThat(landscape.domains().get("connectors").status()).isEqualTo(ComponentStatus.DEGRADED);
        assertThat(landscape.metadata()).containsKey("simulation");
    }

    @Test
    void emptyLandscape() {
        var landscape = new PlatformLandscape(Map.of(), Map.of());
        assertThat(landscape.domains()).isEmpty();
        assertThat(landscape.metadata()).isEmpty();
    }
}
