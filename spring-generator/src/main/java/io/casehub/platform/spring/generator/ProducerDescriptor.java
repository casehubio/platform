package io.casehub.platform.spring.generator;

import com.palantir.javapoet.TypeName;

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
        boolean cdiDeps,
        List<ConstructorParam> constructorParams,
        String configPropertiesPrefix,
        String configPropertiesInterface,
        List<String> qualifiers,
        String initMethod,
        boolean primaryBean,
        int orderValue,
        boolean hasFactoryMethod,
        String factoryMethodName,
        List<ConfigPropertyMethod> configMethods,
        String spiInterface) {

    public record ConfigPropertyMethod(String name, String type, String defaultValue) {}

    public enum ParamKind {
        PLAIN, LIST, OPTIONAL, CONFIG_PROPERTIES, EVENT_CONSUMER, SUPPLIER_DEP
    }

    public record ConstructorParam(String type, String name, ParamKind kind, TypeName resolvedType) {

        public ConstructorParam(String type, String name, ParamKind kind) {
            this(type, name, kind, null);
        }

        public String simpleType() {
            int dot = type.lastIndexOf('.');
            return dot >= 0 ? type.substring(dot + 1) : type;
        }
    }

    public record ParameterDescriptor(String type, String name, String configProperty) {

        public boolean isConfigProperty() {
            return configProperty != null;
        }
    }

    public ProducerDescriptor(
            String producerClassName,
            String methodName,
            String returnType,
            String concreteReturnType,
            List<ParameterDescriptor> parameters,
            boolean defaultBean,
            boolean alternative,
            int priority,
            boolean cdiDeps) {
        this(producerClassName, methodName, returnType, concreteReturnType,
                parameters, defaultBean, alternative, priority, cdiDeps,
                null, null, null, List.of(), null, false, 0, false, null, List.of(), null);
    }

    public boolean constructorResolved() {
        return constructorParams != null;
    }

    public boolean hasConfigProperties() {
        return parameters.stream().anyMatch(ParameterDescriptor::isConfigProperty);
    }

    public boolean hasCdiDependencies() {
        return cdiDeps;
    }

    public boolean requiresManualConfig() {
        if (constructorResolved()) {
            return false;
        }
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

    public boolean isEffectivePrimary() {
        return alternative || primaryBean;
    }

    public boolean hasQualifiers() {
        return qualifiers != null && !qualifiers.isEmpty();
    }
}
