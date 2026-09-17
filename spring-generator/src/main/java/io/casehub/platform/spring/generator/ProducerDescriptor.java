package io.casehub.platform.spring.generator;

import java.util.List;

public record ProducerDescriptor(
        String producerClassName,
        String methodName,
        String returnType,
        String concreteReturnType,
        List<ParameterDescriptor> parameters,
        boolean defaultBean,
        boolean alternative,
        int priority,
        boolean cdiDeps) {

    public record ParameterDescriptor(String type, String name, String configProperty) {

        public boolean isConfigProperty() {
            return configProperty != null;
        }
    }

    public boolean hasConfigProperties() {
        return parameters.stream().anyMatch(ParameterDescriptor::isConfigProperty);
    }

    public boolean hasCdiDependencies() {
        return cdiDeps;
    }

    public boolean requiresManualConfig() {
        return hasConfigProperties() || hasCdiDependencies();
    }

    public boolean hasConcreteOverride() {
        return concreteReturnType != null && !concreteReturnType.equals(returnType);
    }

    public String effectiveReturnType() {
        return concreteReturnType != null ? concreteReturnType : returnType;
    }

    public String returnTypeSimpleName() {
        int dot = returnType.lastIndexOf('.');
        return dot >= 0 ? returnType.substring(dot + 1) : returnType;
    }

    public String effectiveReturnTypeSimpleName() {
        String t = effectiveReturnType();
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }
}
