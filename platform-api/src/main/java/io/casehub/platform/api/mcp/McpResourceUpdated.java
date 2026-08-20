package io.casehub.platform.api.mcp;

import java.util.Objects;

public record McpResourceUpdated(String uri) {
    public McpResourceUpdated {
        Objects.requireNonNull(uri, "uri");
    }
}
