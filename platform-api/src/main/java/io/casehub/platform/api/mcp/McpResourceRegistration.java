package io.casehub.platform.api.mcp;

import java.util.List;
import java.util.function.Supplier;

public interface McpResourceRegistration {
    McpResourceRegistration handler(McpResourceHandler handler);
    McpResourceRegistration completion(String argumentName, Supplier<List<String>> values);
    McpResourceRegistration serverName(String serverName);
    McpResourceHandle register();
}
