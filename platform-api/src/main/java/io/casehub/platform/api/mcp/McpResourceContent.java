package io.casehub.platform.api.mcp;

import java.util.Objects;

public record McpResourceContent(
    String uri,
    String text,
    String mimeType
) {
    public McpResourceContent {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(text, "text");
    }

    public static McpResourceContent of(String uri, String text) {
        return new McpResourceContent(uri, text, null);
    }

    public static McpResourceContent of(String uri, String text, String mimeType) {
        return new McpResourceContent(uri, text, mimeType);
    }
}
