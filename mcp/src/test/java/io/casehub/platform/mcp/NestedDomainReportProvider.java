package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.ComponentStatus;
import io.casehub.platform.api.mcp.DomainReport;
import io.casehub.platform.api.mcp.DomainReportProvider;
import io.casehub.platform.api.mcp.McpDomain;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@McpDomain("nested/child")
@ApplicationScoped
public class NestedDomainReportProvider implements DomainReportProvider {

    @Override
    public DomainReport report(String domain) {
        return new DomainReport(domain, ComponentStatus.AVAILABLE, Map.of(), Map.of());
    }
}
