package io.casehub.platform.api.mcp;

import java.util.Objects;

public record McpResourceRegistered(McpResourceDescriptor descriptor) {
    public McpResourceRegistered {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
