package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.ComponentStatus;
import io.casehub.platform.api.mcp.DomainReport;
import io.casehub.platform.api.mcp.DomainReportProvider;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ProviderReport;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@McpDomain("test")
@ApplicationScoped
public class TestDomainReportProvider implements DomainReportProvider {

    @Override
    public DomainReport report(String domain) {
        return new DomainReport(domain, ComponentStatus.AVAILABLE,
                Map.of("items", new ProviderReport("inmem", ComponentStatus.AVAILABLE,
                        List.of("Create", "Read", "Delete"), Map.of())),
                Map.of("itemCount", 3));
    }
}
