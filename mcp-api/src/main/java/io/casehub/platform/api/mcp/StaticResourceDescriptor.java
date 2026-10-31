package io.casehub.platform.api.mcp;

import java.util.Objects;

public record StaticResourceDescriptor(
    String name,
    String uri,
    String mimeType,
    String description,
    boolean subscribable
) implements McpResourceDescriptor {

    public StaticResourceDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(description, "description");
    }

    public StaticResourceDescriptor withSubscribable(boolean subscribable) {
        return new StaticResourceDescriptor(name, uri, mimeType, description, subscribable);
    }
}
