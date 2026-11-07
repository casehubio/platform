package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpParamFormat;

import java.util.Map;

public record ParameterDescriptor(
        String name,
        String typeName,
        boolean required,
        String description,
        Map<String, String> fields,
        McpParamFormat mcpFormat) {

    public ParameterDescriptor(String name, String typeName, boolean required) {
        this(name, typeName, required, "", Map.of(), null);
    }

    public ParameterDescriptor(String name, String typeName, boolean required,
                               String description, Map<String, String> fields) {
        this(name, typeName, required, description, fields, null);
    }

    public boolean hasMcpFormat() {
        return mcpFormat != null;
    }
}
