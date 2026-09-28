package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.ComponentStatus;
import io.casehub.platform.api.mcp.DomainReport;
import io.casehub.platform.api.mcp.DomainReportProvider;

import java.util.Map;

public class NoOpDomainReportProvider implements DomainReportProvider {

    @Override
    public DomainReport report(String domain) {
        return new DomainReport(domain, ComponentStatus.AVAILABLE, Map.of(), Map.of());
    }
}
