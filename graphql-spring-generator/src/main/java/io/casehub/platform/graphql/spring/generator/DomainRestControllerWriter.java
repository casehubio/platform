package io.casehub.platform.graphql.spring.generator;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class DomainRestControllerWriter {

    private static final ClassName REST_CONTROLLER = ClassName.get("org.springframework.web.bind.annotation", "RestController");
    private static final ClassName REQUEST_MAPPING = ClassName.get("org.springframework.web.bind.annotation", "RequestMapping");
    private static final ClassName GET_MAPPING = ClassName.get("org.springframework.web.bind.annotation", "GetMapping");
    private static final ClassName POST_MAPPING = ClassName.get("org.springframework.web.bind.annotation", "PostMapping");
    private static final ClassName REQUEST_PARAM = ClassName.get("org.springframework.web.bind.annotation", "RequestParam");
    private static final ClassName RESPONSE_ENTITY = ClassName.get("org.springframework.http", "ResponseEntity");
    private static final ClassName MEDIA_TYPE = ClassName.get("org.springframework.http", "MediaType");

    public JavaFile generate(DomainDescriptor descriptor, String targetPackage) {
        String className = capitalize(descriptor.domainName()) + "RestController";
        ClassName spiType = ClassName.bestGuess(descriptor.spiInterfaceName());
        String fieldName = decapitalize(simpleClassName(descriptor.spiInterfaceName()));

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(REST_CONTROLLER)
                .addAnnotation(AnnotationSpec.builder(REQUEST_MAPPING)
                        .addMember("value", "$S", "/api/" + descriptor.domainName())
                        .addMember("produces", "$T.APPLICATION_JSON_VALUE", MEDIA_TYPE)
                        .build());

        classBuilder.addField(FieldSpec.builder(spiType, fieldName, Modifier.PRIVATE, Modifier.FINAL).build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(spiType, fieldName)
                .addStatement("this.$L = $L", fieldName, fieldName)
                .build());

        for (DomainDescriptor.OperationDescriptor op : descriptor.operations()) {
            classBuilder.addMethod(buildRestMethod(op, fieldName));
        }

        return JavaFile.builder(targetPackage, classBuilder.build()).build();
    }

    private MethodSpec buildRestMethod(DomainDescriptor.OperationDescriptor op, String fieldName) {
        ClassName mappingAnnotation = op.operationType() == DomainDescriptor.OperationType.QUERY
                ? GET_MAPPING : POST_MAPPING;

        TypeName returnType = wrapReturnType(op.returnType());

        MethodSpec.Builder builder = MethodSpec.methodBuilder(op.methodName())
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addAnnotation(AnnotationSpec.builder(mappingAnnotation)
                        .addMember("value", "$S", "/" + op.methodName())
                        .build());

        for (DomainDescriptor.ParameterDescriptor param : op.parameters()) {
            builder.addParameter(ParameterSpec.builder(param.type(), param.name())
                    .addAnnotation(AnnotationSpec.builder(REQUEST_PARAM)
                            .addMember("value", "$S", param.name())
                            .build())
                    .build());
        }

        String args = op.parameters().stream()
                .map(DomainDescriptor.ParameterDescriptor::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        if (op.returnType().equals(TypeName.VOID)) {
            builder.addStatement("$L.$L($L)", fieldName, op.methodName(), args);
            builder.addStatement("return $T.noContent().build()", RESPONSE_ENTITY);
        } else {
            builder.addStatement("return $T.ok($L.$L($L))", RESPONSE_ENTITY, fieldName, op.methodName(), args);
        }

        return builder.build();
    }

    private TypeName wrapReturnType(TypeName originalType) {
        if (originalType.equals(TypeName.VOID)) {
            return ParameterizedTypeName.get(RESPONSE_ENTITY, ClassName.get("java.lang", "Void"));
        }
        return ParameterizedTypeName.get(RESPONSE_ENTITY, originalType.box());
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
