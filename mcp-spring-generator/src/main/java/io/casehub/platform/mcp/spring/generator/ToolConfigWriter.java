package io.casehub.platform.mcp.spring.generator;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolConfigWriter {

    private static final ClassName CONFIGURATION = ClassName.get("org.springframework.context.annotation", "Configuration");
    private static final ClassName SPRING_TOOL = ClassName.get("org.springframework.ai.tool.annotation", "Tool");
    private static final ClassName SPRING_TOOL_PARAM = ClassName.get("org.springframework.ai.tool.annotation", "ToolParam");

    public JavaFile generate(String sourceClassName, List<ToolDescriptor> tools, String targetPackage) {
        String simpleName = simpleClassName(sourceClassName);
        String configName = "Spring" + simpleName;

        ClassName sourceType = ClassName.bestGuess(sourceClassName);
        String fieldName = decapitalize(simpleName);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(configName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CONFIGURATION);

        classBuilder.addField(FieldSpec.builder(sourceType, fieldName, Modifier.PRIVATE, Modifier.FINAL).build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(sourceType, fieldName)
                .addStatement("this.$L = $L", fieldName, fieldName)
                .build());

        for (ToolDescriptor tool : tools) {
            classBuilder.addMethod(buildToolMethod(tool, fieldName));
        }

        return JavaFile.builder(targetPackage, classBuilder.build()).build();
    }

    private MethodSpec buildToolMethod(ToolDescriptor tool, String fieldName) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(tool.methodName())
                .addModifiers(Modifier.PUBLIC)
                .returns(tool.returnType())
                .addAnnotation(AnnotationSpec.builder(SPRING_TOOL)
                        .addMember("value", "$S", tool.description())
                        .build());

        for (ToolDescriptor.ToolParameterDescriptor param : tool.parameters()) {
            ParameterSpec.Builder paramBuilder = ParameterSpec.builder(param.type(), param.name());
            if (!param.description().isEmpty()) {
                paramBuilder.addAnnotation(AnnotationSpec.builder(SPRING_TOOL_PARAM)
                        .addMember("description", "$S", param.description())
                        .build());
            }
            builder.addParameter(paramBuilder.build());
        }

        String args = tool.parameters().stream()
                .map(ToolDescriptor.ToolParameterDescriptor::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        if (tool.returnType().equals(com.palantir.javapoet.TypeName.VOID)) {
            builder.addStatement("$L.$L($L)", fieldName, tool.methodName(), args);
        } else {
            builder.addStatement("return $L.$L($L)", fieldName, tool.methodName(), args);
        }

        return builder.build();
    }

    public Map<String, List<ToolDescriptor>> groupByClass(List<ToolDescriptor> tools) {
        Map<String, List<ToolDescriptor>> grouped = new LinkedHashMap<>();
        for (ToolDescriptor tool : tools) {
            grouped.computeIfAbsent(tool.className(), k -> new java.util.ArrayList<>()).add(tool);
        }
        return grouped;
    }

    private String simpleClassName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
