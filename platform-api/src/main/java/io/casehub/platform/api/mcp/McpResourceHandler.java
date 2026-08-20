package io.casehub.platform.api.mcp;

@FunctionalInterface
public interface McpResourceHandler {
    McpResourceContent read(McpResourceReadRequest request) throws Exception;
}
