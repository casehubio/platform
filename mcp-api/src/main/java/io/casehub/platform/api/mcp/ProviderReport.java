package io.casehub.platform.api.mcp;

import java.util.List;
import java.util.Map;

public record ProviderReport(
    String providerId,
    ComponentStatus status,
    List<String> capabilities,
    Map<String, Object> metadata
) {}
