package io.casehub.platform.api.mcp;

import java.util.Map;
import java.util.Objects;

public record McpResourceReadRequest(
    String uri,
    Map<String, String> templateArgs
) {
    public McpResourceReadRequest {
        Objects.requireNonNull(uri, "uri");
        templateArgs = templateArgs != null ? Map.copyOf(templateArgs) : Map.of();
    }

    public static McpResourceReadRequest of(String uri) {
        return new McpResourceReadRequest(uri, Map.of());
    }
}
