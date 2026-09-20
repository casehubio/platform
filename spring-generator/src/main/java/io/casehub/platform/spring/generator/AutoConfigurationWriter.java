package io.casehub.platform.spring.generator;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoConfigurationWriter {

    private static final ClassName AUTO_CONFIGURATION = ClassName.get(
            "org.springframework.boot.autoconfigure", "AutoConfiguration");
    private static final ClassName BEAN = ClassName.get(
            "org.springframework.context.annotation", "Bean");
    private static final ClassName CONDITIONAL_ON_MISSING_BEAN = ClassName.get(
            "org.springframework.boot.autoconfigure.condition", "ConditionalOnMissingBean");
    private static final ClassName CONDITIONAL_ON_CLASS = ClassName.get(
            "org.springframework.boot.autoconfigure.condition", "ConditionalOnClass");
    private static final ClassName PRIMARY = ClassName.get(
            "org.springframework.context.annotation", "Primary");
    private static final ClassName ORDER = ClassName.get(
            "org.springframework.core.annotation", "Order");
    private static final ClassName ENABLE_CONFIG_PROPERTIES = ClassName.get(
            "org.springframework.boot.context.properties", "EnableConfigurationProperties");
    private static final ClassName CONFIG_PROPERTIES = ClassName.get(
            "org.springframework.boot.context.properties", "ConfigurationProperties");
    private static final ClassName DEFAULT_VALUE = ClassName.get(
            "org.springframework.boot.context.properties.bind", "DefaultValue");
    private static final ClassName VALUE = ClassName.get(
            "org.springframework.beans.factory.annotation", "Value");
    private static final ClassName OBJECT_PROVIDER = ClassName.get(
            "org.springframework.beans.factory", "ObjectProvider");
    private static final ClassName APPLICATION_EVENT_PUBLISHER = ClassName.get(
            "org.springframework.context", "ApplicationEventPublisher");

    public List<JavaFile> generate(String packageName, String className,
                                   List<ProducerDescriptor> descriptors) {
        var files = new ArrayList<JavaFile>();

        ClassName anchorType = selectAnchorType(descriptors);

        // Collect ALL unique config properties interfaces across descriptors
        var configRecordMap = new java.util.LinkedHashMap<String, ClassName>();
        for (ProducerDescriptor d : descriptors) {
            if (d.requiresManualConfig()) continue;
            if (d.configPropertiesInterface() != null && !d.configMethods().isEmpty()
                    && !configRecordMap.containsKey(d.configPropertiesInterface())) {
                ClassName recordType = ClassName.get(packageName,
                        simpleClassName(d.configPropertiesInterface()) + "Spring");
                configRecordMap.put(d.configPropertiesInterface(), recordType);
                files.add(generateConfigPropertiesRecord(packageName, d, recordType));
            }
        }

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AUTO_CONFIGURATION);

        if (anchorType != null) {
            classBuilder.addAnnotation(AnnotationSpec.builder(CONDITIONAL_ON_CLASS)
                    .addMember("value", "$T.class", anchorType)
                    .build());
        }

        if (!configRecordMap.isEmpty()) {
            var enableBuilder = AnnotationSpec.builder(ENABLE_CONFIG_PROPERTIES);
            for (ClassName recordType : configRecordMap.values()) {
                enableBuilder.addMember("value", "$T.class", recordType);
            }
            classBuilder.addAnnotation(enableBuilder.build());
        }

        for (ProducerDescriptor d : descriptors) {
            if (d.requiresManualConfig()) continue;

            if (d.constructorResolved()) {
                ClassName propsType = d.configPropertiesInterface() != null
                        ? configRecordMap.get(d.configPropertiesInterface()) : null;
                classBuilder.addMethod(buildEnhancedBeanMethod(d, propsType));
            } else {
                classBuilder.addMethod(buildLegacyBeanMethod(d));
            }
        }

        files.add(0, JavaFile.builder(packageName, classBuilder.build()).build());
        return files;
    }

    public String generateImportsFile(String packageName, String className) {
        return packageName + "." + className + "\n";
    }

    private MethodSpec buildEnhancedBeanMethod(ProducerDescriptor d, ClassName propsRecordType) {
        ClassName returnType = toClassName(d.effectiveReturnType());
        MethodSpec.Builder builder = MethodSpec.methodBuilder(d.methodName())
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType);

        addBeanAnnotation(builder, d);

        if (d.defaultBean()) {
            if (d.hasConcreteOverride()) {
                builder.addAnnotation(AnnotationSpec.builder(CONDITIONAL_ON_MISSING_BEAN)
                        .addMember("value", "$T.class", toClassName(d.returnType()))
                        .build());
            } else {
                builder.addAnnotation(CONDITIONAL_ON_MISSING_BEAN);
            }
        }
        if (d.isEffectivePrimary()) {
            builder.addAnnotation(PRIMARY);
        }
        if (d.orderValue() > 0) {
            builder.addAnnotation(AnnotationSpec.builder(ORDER)
                    .addMember("value", "$L", d.orderValue())
                    .build());
        }
        for (String qualifier : d.qualifiers()) {
            builder.addAnnotation(toClassName(qualifier));
        }

        boolean needsPublisher = d.constructorParams().stream()
                .anyMatch(cp -> cp.kind() == ProducerDescriptor.ParamKind.EVENT_CONSUMER);
        if (needsPublisher) {
            builder.addParameter(APPLICATION_EVENT_PUBLISHER, "publisher");
        }

        var paramNames = new ArrayList<String>();
        for (ProducerDescriptor.ConstructorParam cp : d.constructorParams()) {
            switch (cp.kind()) {
                case LIST -> {
                    ParameterizedTypeName listType = ParameterizedTypeName.get(
                            ClassName.get(java.util.List.class), toClassName(cp.type()));
                    builder.addParameter(listType, cp.name());
                    paramNames.add(cp.name());
                }
                case OPTIONAL -> {
                    ParameterizedTypeName providerType = ParameterizedTypeName.get(
                            OBJECT_PROVIDER, toClassName(cp.type()));
                    builder.addParameter(providerType, cp.name());
                    if (d.hasFactoryMethod()) {
                        paramNames.add("java.util.Optional.ofNullable(" + cp.name() + ".getIfAvailable())");
                    } else {
                        paramNames.add(cp.name() + ".getIfAvailable()");
                    }
                }
                case CONFIG_PROPERTIES -> {
                    TypeName paramType = propsRecordType != null ? propsRecordType : toClassName(cp.type());
                    builder.addParameter(paramType, cp.name());
                    paramNames.add(cp.name());
                }
                case EVENT_CONSUMER -> {
                    paramNames.add("event -> publisher.publishEvent(event)");
                }
                case SUPPLIER_DEP -> {
                    ParameterizedTypeName providerType = ParameterizedTypeName.get(
                            OBJECT_PROVIDER, toClassName(cp.type()));
                    builder.addParameter(providerType, cp.name());
                    paramNames.add(cp.name() + ".getIfAvailable() != null ? " + cp.name() + "::getObject : null");
                }
                case PLAIN -> {
                    builder.addParameter(toClassName(cp.type()), cp.name());
                    paramNames.add(cp.name());
                }
            }
        }

        String args = String.join(", ", paramNames);
        if (d.hasFactoryMethod()) {
            builder.addStatement("return $T.$L($L)", returnType, d.factoryMethodName(), args);
        } else {
            builder.addStatement("return new $T($L)", returnType, args);
        }

        return builder.build();
    }

    private MethodSpec buildLegacyBeanMethod(ProducerDescriptor d) {
        ClassName returnType = toClassName(d.returnType());
        ClassName effectiveReturn = toClassName(d.effectiveReturnType());
        MethodSpec.Builder builder = MethodSpec.methodBuilder(d.methodName())
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addAnnotation(BEAN);

        if (d.defaultBean()) {
            if (d.hasConcreteOverride()) {
                builder.addAnnotation(AnnotationSpec.builder(CONDITIONAL_ON_MISSING_BEAN)
                        .addMember("value", "$T.class", returnType)
                        .build());
            } else {
                builder.addAnnotation(CONDITIONAL_ON_MISSING_BEAN);
            }
        }
        if (d.alternative()) {
            builder.addAnnotation(PRIMARY);
        }

        var paramNames = new ArrayList<String>();
        for (ProducerDescriptor.ParameterDescriptor p : d.parameters()) {
            ClassName paramType = toClassName(p.type());
            if (p.isConfigProperty()) {
                builder.addParameter(ParameterSpec.builder(paramType, p.name())
                        .addAnnotation(AnnotationSpec.builder(VALUE)
                                .addMember("value", "$S", "${" + p.configProperty() + "}")
                                .build())
                        .build());
            } else {
                builder.addParameter(paramType, p.name());
            }
            paramNames.add(p.name());
        }

        String args = String.join(", ", paramNames);
        builder.addStatement("return new $T($L)", effectiveReturn, args);
        return builder.build();
    }

    private void addBeanAnnotation(MethodSpec.Builder builder, ProducerDescriptor d) {
        if (d.initMethod() != null && !d.initMethod().isEmpty()) {
            builder.addAnnotation(AnnotationSpec.builder(BEAN)
                    .addMember("initMethod", "$S", d.initMethod())
                    .build());
        } else {
            builder.addAnnotation(BEAN);
        }
    }

    private JavaFile generateConfigPropertiesRecord(String packageName,
                                                     ProducerDescriptor d,
                                                     ClassName recordType) {
        ClassName propsInterface = toClassName(d.configPropertiesInterface());

        MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder();
        for (ProducerDescriptor.ConfigPropertyMethod m : d.configMethods()) {
            ParameterSpec.Builder paramBuilder = ParameterSpec.builder(
                    toClassName(m.type()), m.name());
            if (m.defaultValue() != null) {
                paramBuilder.addAnnotation(AnnotationSpec.builder(DEFAULT_VALUE)
                        .addMember("value", "$S", m.defaultValue())
                        .build());
            }
            ctorBuilder.addParameter(paramBuilder.build());
        }

        TypeSpec record = TypeSpec.recordBuilder(recordType.simpleName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(CONFIG_PROPERTIES)
                        .addMember("prefix", "$S", d.configPropertiesPrefix())
                        .build())
                .addSuperinterface(propsInterface)
                .recordConstructor(ctorBuilder.build())
                .build();

        return JavaFile.builder(packageName, record).build();
    }

    private ClassName selectAnchorType(List<ProducerDescriptor> descriptors) {
        return descriptors.stream()
                .filter(d -> !d.requiresManualConfig())
                .map(d -> d.effectiveReturnType())
                .sorted()
                .findFirst()
                .map(this::toClassName)
                .orElse(null);
    }

    private ClassName toClassName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot < 0) {
            return ClassName.get("", fqn);
        }
        return ClassName.get(fqn.substring(0, lastDot), fqn.substring(lastDot + 1));
    }

    private String simpleClassName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
