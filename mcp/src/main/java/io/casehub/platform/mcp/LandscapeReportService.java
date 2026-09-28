package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.ComponentStatus;
import io.casehub.platform.api.mcp.DomainReport;
import io.casehub.platform.api.mcp.DomainReportProvider;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@McpDomain(value = "landscape",
           summary = "Cross-domain aggregated view of the platform landscape")
@ApplicationScoped
public class LandscapeReportService {

    private static final Logger LOG = Logger.getLogger(LandscapeReportService.class);
    private static final long DOMAIN_TIMEOUT_SECONDS = 10;

    @Inject
    DomainModelRegistry registry;

    @PlatformQuery("Full platform landscape — all domains, providers, and status. " +
                    "Use scope to filter: 'all' (default), single domain name, " +
                    "or comma-separated domain names.")
    public PlatformLandscape landscapeReport(String scope) {
        Map<String, DomainReportProvider> providers = registry.getReportProviders();

        Set<String> scopeFilter = null;
        if (scope != null && !scope.isBlank() && !"all".equalsIgnoreCase(scope)) {
            scopeFilter = Arrays.stream(scope.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
        }

        Map<String, DomainReport> domains = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<DomainReport>> futures = new LinkedHashMap<>();

            for (var entry : providers.entrySet()) {
                String domain = entry.getKey();
                if (scopeFilter != null && !scopeFilter.contains(domain)) {
                    continue;
                }
                DomainReportProvider provider = entry.getValue();
                futures.put(domain, executor.submit(() -> provider.report(domain)));
            }

            for (var entry : futures.entrySet()) {
                String domain = entry.getKey();
                try {
                    DomainReport report = entry.getValue().get(
                            DOMAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    domains.put(domain, report);
                } catch (Exception e) {
                    LOG.warnf(e, "Report for domain '%s' failed or timed out", domain);
                    domains.put(domain, new DomainReport(
                            domain, ComponentStatus.UNAVAILABLE, Map.of(),
                            Map.of("error", e.getMessage() != null ? e.getMessage() : "timeout")));
                }
            }
        }

        return new PlatformLandscape(Map.copyOf(domains), Map.of());
    }
}
