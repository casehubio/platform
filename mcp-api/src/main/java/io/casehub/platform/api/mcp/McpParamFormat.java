package io.casehub.platform.api.mcp;

/**
 * Declares how the MCP generator should project a parameter
 * that the Java SPI accepts as a plain String.
 */
public enum McpParamFormat {
    /**
     * MCP tool accepts a JSON object ({@code Map<String, String>}).
     * The dispatcher serialises it to a pipe-separated
     * {@code key=value|key=value} string before calling the bean.
     */
    MAP,

    /**
     * MCP tool accepts a JSON array ({@code List<String>}).
     * The dispatcher serialises it to a pipe-separated
     * {@code value|value|value} string before calling the bean.
     */
    LIST
}
