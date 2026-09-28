package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.ComponentStatus;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class LandscapeReportServiceTest {

    @Inject
    ToolManager toolManager;

    @Inject
    LandscapeReportService landscapeService;
    @Inject
    DomainModelRegistry    registry;


    @Test
    void landscapeReport_includesTestDomain() {
        var landscape = landscapeService.landscapeReport(null);
        assertThat(landscape.domains()).containsKey("test");
        assertThat(landscape.domains().get("test").status()).isEqualTo(ComponentStatus.AVAILABLE);
    }

    @Test
    void landscapeReport_testDomainHasProviders() {
        var landscape = landscapeService.landscapeReport(null);
        var testDomain = landscape.domains().get("test");
        assertThat(testDomain.providers()).containsKey("items");
        assertThat(testDomain.providers().get("items").providerId()).isEqualTo("inmem");
    }

    @Test
    void landscapeReport_scopeFiltersToSingleDomain() {
        var landscape = landscapeService.landscapeReport("test");
        assertThat(landscape.domains()).containsKey("test");
        assertThat(landscape.domains()).hasSize(1);
    }

    @Test
    void landscapeReport_scopeAll_includesAllDomains() {
        var landscape = landscapeService.landscapeReport("all");
        assertThat(landscape.domains()).isNotEmpty();
        assertThat(landscape.domains()).containsKey("test");
    }

    @Test
    void landscapeReport_unknownScope_returnsEmpty() {
        var landscape = landscapeService.landscapeReport("nonexistent");
        assertThat(landscape.domains()).isEmpty();
    }

    @Test
    void landscapeReport_nullScope_returnsAll() {
        var landscape = landscapeService.landscapeReport(null);
        assertThat(landscape.domains()).isNotEmpty();
    }

    @Test
    void landscapeDomain_isDiscoveredByScanner() {
        var domain = registry.getDomain("landscape");
        assertThat(domain).isPresent();
        assertThat(domain.get().operations()).isNotEmpty();
        assertThat(domain.get().operations().stream()
                         .anyMatch(op -> op.name().equals("landscapeReport"))).isTrue();
    }
}
