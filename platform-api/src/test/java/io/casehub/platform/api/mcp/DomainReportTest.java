package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DomainReportTest {

    @Test
    void simpleDomain_emptyProviders() {
        var report = new DomainReport("work", ComponentStatus.AVAILABLE,
                Map.of(), Map.of("openWorkItems", 42));

        assertThat(report.domain()).isEqualTo("work");
        assertThat(report.status()).isEqualTo(ComponentStatus.AVAILABLE);
        assertThat(report.providers()).isEmpty();
        assertThat(report.metadata()).containsEntry("openWorkItems", 42);
    }

    @Test
    void domainWithProviders() {
        var emailProvider = new ProviderReport("google", ComponentStatus.AVAILABLE,
                List.of("Send", "Search"), Map.of());
        var bankProvider = new ProviderReport("truelayer", ComponentStatus.DEGRADED,
                List.of("AccountQuery"), Map.of("reason", "rate limited"));

        var report = new DomainReport("connectors", ComponentStatus.DEGRADED,
                Map.of("email", emailProvider, "bank", bankProvider),
                Map.of());

        assertThat(report.providers()).hasSize(2);
        assertThat(report.providers().get("email").providerId()).isEqualTo("google");
        assertThat(report.providers().get("bank").status()).isEqualTo(ComponentStatus.DEGRADED);
    }

    @Test
    void providerReport_capabilitiesAreAccessible() {
        var provider = new ProviderReport("slack", ComponentStatus.AVAILABLE,
                List.of("Messaging", "Discovery", "Reactions"), Map.of());

        assertThat(provider.capabilities()).containsExactly("Messaging", "Discovery", "Reactions");
    }

    @Test
    void componentStatus_allValues() {
        assertThat(ComponentStatus.values()).containsExactly(
                ComponentStatus.AVAILABLE, ComponentStatus.DEGRADED, ComponentStatus.UNAVAILABLE);
    }

    @Test
    void domainReportProvider_isFunctionalInterface() {
        DomainReportProvider provider = domain ->
                new DomainReport(domain, ComponentStatus.AVAILABLE, Map.of(), Map.of());

        var report = provider.report("test");
        assertThat(report.domain()).isEqualTo("test");
        assertThat(report.status()).isEqualTo(ComponentStatus.AVAILABLE);
    }
}
