package io.casehub.platform.api.mcp;

import java.util.Map;

public record DomainReport(
    String domain,
    ComponentStatus status,
    Map<String, ProviderReport> providers,
    Map<String, Object> metadata
) {}
