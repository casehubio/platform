package io.casehub.platform.graphql.spring.generator;

import com.palantir.javapoet.TypeName;

import java.util.List;

public record DomainDescriptor(
        String domainName,
        String spiInterfaceName,
        List<OperationDescriptor> operations
) {
    public record OperationDescriptor(
            String methodName,
            OperationType operationType,
            String description,
            TypeName returnType,
            List<ParameterDescriptor> parameters
    ) {}

    public record ParameterDescriptor(
            String name,
            TypeName type
    ) {}

    public enum OperationType {
        QUERY, MUTATION
    }
}
