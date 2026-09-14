package io.casehub.platform.graphql.spring.generator;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class GraphqlControllerWriter {

    private static final ClassName CONTROLLER = ClassName.get("org.springframework.stereotype", "Controller");
    private static final ClassName QUERY_MAPPING = ClassName.get("org.springframework.graphql.data.method.annotation", "QueryMapping");
    private static final ClassName MUTATION_MAPPING = ClassName.get("org.springframework.graphql.data.method.annotation", "MutationMapping");
    private static final ClassName ARGUMENT = ClassName.get("org.springframework.graphql.data.method.annotation", "Argument");

    public JavaFile generate(DomainDescriptor descriptor, String targetPackage) {
        String className = capitalize(descriptor.domainName()) + "GraphqlController";
        ClassName spiType = ClassName.bestGuess(descriptor.spiInterfaceName());
        String fieldName = decapitalize(simpleClassName(descriptor.spiInterfaceName()));

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CONTROLLER);

        classBuilder.addField(FieldSpec.builder(spiType, fieldName, Modifier.PRIVATE, Modifier.FINAL).build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(spiType, fieldName)
                .addStatement("this.$L = $L", fieldName, fieldName)
                .build());

        for (DomainDescriptor.OperationDescriptor op : descriptor.operations()) {
            classBuilder.addMethod(buildMethod(op, fieldName));
        }

        return JavaFile.builder(targetPackage, classBuilder.build()).build();
    }

    private MethodSpec buildMethod(DomainDescriptor.OperationDescriptor op, String fieldName) {
        ClassName mappingAnnotation = op.operationType() == DomainDescriptor.OperationType.QUERY
                ? QUERY_MAPPING : MUTATION_MAPPING;

        MethodSpec.Builder builder = MethodSpec.methodBuilder(op.methodName())
                .addModifiers(Modifier.PUBLIC)
                .returns(op.returnType())
                .addAnnotation(mappingAnnotation);

        for (DomainDescriptor.ParameterDescriptor param : op.parameters()) {
            builder.addParameter(ParameterSpec.builder(param.type(), param.name())
                    .addAnnotation(AnnotationSpec.builder(ARGUMENT)
                            .addMember("name", "$S", param.name())
                            .build())
                    .build());
        }

        String args = op.parameters().stream()
                .map(DomainDescriptor.ParameterDescriptor::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        if (op.returnType().equals(com.palantir.javapoet.TypeName.VOID)) {
            builder.addStatement("$L.$L($L)", fieldName, op.methodName(), args);
        } else {
            builder.addStatement("return $L.$L($L)", fieldName, op.methodName(), args);
        }

        return builder.build();
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private String simpleClassName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
