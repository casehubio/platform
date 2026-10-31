package io.casehub.platform.api.mcp;

public interface McpResourceHandle {
    void notifyUpdate(String uri);
    void deregister();
}
