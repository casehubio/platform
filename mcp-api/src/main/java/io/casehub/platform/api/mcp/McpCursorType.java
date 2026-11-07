package io.casehub.platform.api.mcp;

/**
 * Cursor mapping types for {@link McpCursorMap}.
 */
public enum McpCursorType {
    /**
     * The MCP tool accepts an ISO-8601 timestamp string.
     * The dispatcher converts it to an {@link java.time.Instant}
     * before passing to the SPI method.
     */
    INSTANT
}
