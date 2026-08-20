package io.casehub.platform.api.mcp;

import java.util.List;
import java.util.Optional;

public interface McpResourceRegistry {
    McpResourceRegistration newResource(McpResourceDescriptor descriptor);
    void deregister(String name);
    Optional<McpResourceDescriptor> resolve(String name);
    List<McpResourceDescriptor> list();
}
