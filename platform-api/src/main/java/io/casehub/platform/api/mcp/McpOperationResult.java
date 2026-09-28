package io.casehub.platform.api.mcp;

import java.util.List;

public record McpOperationResult(
    OperationOutcome outcome,
    String domain,
    String operation,
    String capability,
    String provider,
    List<String> supportedCapabilities,
    String message
) {
    public static McpOperationResult from(McpCapabilityException e,
                                           String domain, String operation) {
        return new McpOperationResult(
            e.outcome(), domain, operation,
            e.capability(), e.provider(),
            e.supportedCapabilities(), e.getMessage());
    }
}
