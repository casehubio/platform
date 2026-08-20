package io.casehub.platform.api.mcp;

public sealed interface McpResourceDescriptor
        permits StaticResourceDescriptor, TemplateResourceDescriptor {

    String name();
    String mimeType();
    String description();
    boolean subscribable();

    static StaticResourceDescriptor of(
            String name, String uri, String mimeType, String description) {
        return new StaticResourceDescriptor(name, uri, mimeType, description, false);
    }

    static TemplateResourceDescriptor template(
            String name, String uriTemplate, String mimeType, String description) {
        return new TemplateResourceDescriptor(name, uriTemplate, mimeType, description, false);
    }
}
