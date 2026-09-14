package io.casehub.platform.mcp.spring.generator;

import com.palantir.javapoet.TypeName;

import java.util.List;

public record ToolDescriptor(
        String className,
        String methodName,
        String description,
        TypeName returnType,
        List<ToolParameterDescriptor> parameters
) {
    public record ToolParameterDescriptor(
            String name,
            TypeName type,
            String description
    ) {}
}
