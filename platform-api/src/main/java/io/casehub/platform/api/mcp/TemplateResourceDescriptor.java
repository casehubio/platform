package io.casehub.platform.api.mcp;

import java.util.Objects;

public record TemplateResourceDescriptor(
    String name,
    String uriTemplate,
    String mimeType,
    String description,
    boolean subscribable
) implements McpResourceDescriptor {

    public TemplateResourceDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uriTemplate, "uriTemplate");
        Objects.requireNonNull(description, "description");
    }

    public TemplateResourceDescriptor withSubscribable(boolean subscribable) {
        return new TemplateResourceDescriptor(name, uriTemplate, mimeType, description, subscribable);
    }
}
