package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.DomainReport;

import java.util.Map;

public record PlatformLandscape(
    Map<String, DomainReport> domains,
    Map<String, Object> metadata
) {}
