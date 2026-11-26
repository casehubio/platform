package io.casehub.platform.agent.service.generator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

@SupportedAnnotationTypes("io.casehub.platform.agent.AgentService")
public class AgentServiceProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof TypeElement typeElement && typeElement.getKind().isInterface()) {
                    try {
                        generateProxy(typeElement);
                    } catch (IOException e) {
                        processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR, "Failed to generate proxy: " + e.getMessage(), element);
                    }
                }
            }
        }
        return true;
    }

    private void generateProxy(TypeElement iface) throws IOException {
        String packageName = processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
        String simpleName = iface.getSimpleName().toString();
        String proxyName = simpleName + "Proxy";
        String qualifiedName = packageName.isEmpty() ? proxyName : packageName + "." + proxyName;

        var ann = iface.getAnnotation(io.casehub.platform.agent.AgentService.class);
        String model = ann.model();
        String descriptor = ann.descriptor();
        int timeout = ann.timeout();

        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(qualifiedName, iface);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import io.casehub.platform.agent.AgentProvider;");
            out.println("import io.casehub.platform.agent.AgentResolver;");
            out.println("import io.casehub.platform.agent.AgentSessionConfig;");
            out.println("import io.casehub.platform.agent.AgentSessionInit;");
            out.println("import io.casehub.platform.agent.AgentInvocationResult;");
            out.println("import io.casehub.platform.agent.ResolvedAgent;");
            out.println("import io.casehub.platform.agent.ResponseFormat;");
            out.println("import io.casehub.platform.agent.AgentEvent;");
            out.println("import io.casehub.platform.agent.AgentSession;");
            out.println("import io.smallrye.mutiny.Multi;");
            out.println("import java.time.Duration;");
            out.println("import io.casehub.schema.generator.PlatformSchemaGenerator;");
            out.println("import com.fasterxml.jackson.databind.ObjectMapper;");
            out.println("import io.casehub.platform.agent.AgentResponseParseException;");
            out.println("import com.fasterxml.jackson.core.type.TypeReference;");
            out.println();

            out.println("public class " + proxyName + " implements " + simpleName + " {");
            out.println();
            out.println("    private final AgentProvider agentProvider;");
            out.println("    private final AgentResolver agentResolver;");
            out.println("    private final PlatformSchemaGenerator schemaGenerator;");
            out.println("    private final ObjectMapper objectMapper;");
            out.println();
            out.println("    public " + proxyName + "(AgentProvider agentProvider, AgentResolver agentResolver, PlatformSchemaGenerator schemaGenerator, ObjectMapper objectMapper) {");
            out.println("        this.agentProvider = agentProvider;");
            out.println("        this.agentResolver = agentResolver;");
            out.println("        this.schemaGenerator = schemaGenerator;");
            out.println("        this.objectMapper = objectMapper;");

            boolean hasErrors = false;
            for (Element enclosed : iface.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement m) {
                    if (!isAgentSession(m.getReturnType())) {
                        var qAnn = m.getAnnotation(io.casehub.platform.agent.Query.class);
                        if (qAnn != null && !validateReturnType(m)) {
                            hasErrors = true;
                        }
                    }
                }
            }
            if (hasErrors) return;

            java.util.LinkedHashSet<String> schemaTypes = new java.util.LinkedHashSet<>();
            for (Element enclosed : iface.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement m) {
                    TypeMirror rt = m.getReturnType();
                    if (!isMultiAgentEvent(rt) && !isAgentSession(rt) && !isString(rt)) {
                        if (isCollection(rt)) {
                            TypeMirror elementType = collectionElementType(rt);
                            if (elementType != null) schemaTypes.add(elementType.toString());
                        } else if (rt instanceof DeclaredType) {
                            schemaTypes.add(rt.toString());
                        }
                    }
                }
            }
            for (String schemaType : schemaTypes) {
                String fieldName = schemaFieldName(schemaType);
                out.println("        this." + fieldName + " = schemaGenerator.generate(" + schemaType + ".class).toString();");
            }

            out.println("    }");

            for (String schemaType : schemaTypes) {
                String fieldName = schemaFieldName(schemaType);
                out.println("    private final String " + fieldName + ";");
            }

            for (Element enclosed : iface.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement method) {
                    if (isAgentSession(method.getReturnType())) {
                        generateSessionMethod(out, method, model, descriptor);
                    } else {
                        var queryAnn = method.getAnnotation(io.casehub.platform.agent.Query.class);
                        if (queryAnn == null) continue;
                        generateMethod(out, method, queryAnn, model, descriptor, timeout);
                    }
                }
            }

            out.println("}");
        }
    }

    private void generateMethod(PrintWriter out, ExecutableElement method,
                                 io.casehub.platform.agent.Query queryAnn,
                                 String model, String descriptor, int timeout) {
        TypeMirror returnType = method.getReturnType();
        String returnTypeName = returnType.toString();
        String methodName = method.getSimpleName().toString();
        String queryTemplate = queryAnn.value();

        out.println();
        out.println("    @Override");
        out.print("    public " + returnTypeName + " " + methodName + "(");

        var params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) out.print(", ");
            VariableElement param = params.get(i);
            out.print(param.asType() + " " + param.getSimpleName());
        }
        out.println(") {");

        out.println("        String userPrompt = \"" + escapeJava(queryTemplate) + "\";");
        for (VariableElement param : params) {
            var paramAnn = param.getAnnotation(io.casehub.platform.agent.Param.class);
            if (paramAnn != null) {
                out.println("        userPrompt = userPrompt.replace(\"${" + paramAnn.value() + "}\", String.valueOf(" + param.getSimpleName() + "));");
            }
        }

        out.println("        String systemPrompt = \"\";");
        if (!descriptor.isEmpty()) {
            out.println("        var resolved = agentResolver.resolve(\"" + escapeJava(descriptor) + "\");");
            out.println("        if (resolved.isPresent()) { systemPrompt = resolved.get().systemPrompt(); }");
        }

        out.println("        var config = AgentSessionConfig.of(systemPrompt, userPrompt);");
        if (!model.isEmpty()) {
            out.println("        config = config.withModel(\"" + escapeJava(model) + "\");");
        }

        if (isMultiAgentEvent(returnType)) {
            out.println("        return agentProvider.invoke(config);");
        } else {
            String timeoutExpr = timeout > 0
                ? "Duration.ofSeconds(" + timeout + ")"
                : "Duration.ofSeconds(60)";

            if (isString(returnType)) {
                out.println("        AgentInvocationResult result = agentProvider.invokeBlocking(config, " + timeoutExpr + ");");
                out.println("        return result.text();");
            } else if (isCollection(returnType)) {
                TypeMirror elementType = collectionElementType(returnType);
                String elementTypeName = elementType.toString();
                String elementSimple = simpleTypeName(elementTypeName);
                String kind = collectionKind(returnType);
                String elementFieldName = schemaFieldName(elementTypeName);
                out.println("        String listSchema = \"{\\\"type\\\":\\\"array\\\",\\\"items\\\":\" + " + elementFieldName + " + \"}\";");
                out.println("        config = config.withResponseFormat(ResponseFormat.json(\"" + elementSimple + kind + "\", listSchema));");
                out.println("        AgentInvocationResult result = agentProvider.invokeBlocking(config, " + timeoutExpr + ");");
                out.println("        try {");
                if (kind.equals("Set")) {
                    out.println("            return new java.util.LinkedHashSet<>(objectMapper.readValue(result.text(), new TypeReference<java.util.List<" + elementTypeName + ">>(){}));");
                } else {
                    out.println("            return objectMapper.readValue(result.text(), new TypeReference<java.util.List<" + elementTypeName + ">>(){});");
                }
                out.println("        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {");
                out.println("            throw new AgentResponseParseException(\"Failed to parse response as " + kind + "<" + elementSimple + ">\", e, result.text());");
                out.println("        }");
            } else {
                String qualifiedType = returnType.toString();
                String simpleName = simpleTypeName(qualifiedType);
                String fieldName = schemaFieldName(qualifiedType);
                out.println("        config = config.withResponseFormat(ResponseFormat.json(\"" + simpleName + "\", " + fieldName + "));");
                out.println("        AgentInvocationResult result = agentProvider.invokeBlocking(config, " + timeoutExpr + ");");
                out.println("        try {");
                out.println("            return objectMapper.readValue(result.text(), " + qualifiedType + ".class);");
                out.println("        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {");
                out.println("            throw new AgentResponseParseException(\"Failed to parse response as " + simpleName + "\", e, result.text());");
                out.println("        }");
            }
        }

        out.println("    }");
    }

    private void generateSessionMethod(PrintWriter out, ExecutableElement method,
                                       String model, String descriptor) {
        String returnTypeName = method.getReturnType().toString();
        String methodName     = method.getSimpleName().toString();

        out.println();
        out.println("    @Override");
        out.print("    public " + returnTypeName + " " + methodName + "(");
        var params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {out.print(", ");}
            out.print(params.get(i).asType() + " " + params.get(i).getSimpleName());
        }
        out.println(") {");

        out.println("        String systemPrompt = \"\";");
        if (!descriptor.isEmpty()) {
            out.println("        var resolved = agentResolver.resolve(\"" + escapeJava(descriptor) + "\");");
            out.println("        if (resolved.isPresent()) { systemPrompt = resolved.get().systemPrompt(); }");
        }
        out.println("        var init = AgentSessionInit.of(systemPrompt);");
        if (!model.isEmpty()) {
            out.println("        init = init.withModel(\"" + escapeJava(model) + "\");");
        }
        out.println("        return agentProvider.openSession(init);");
        out.println("    }");
    }


    private static final java.util.Set<String> BOXED_PRIMITIVES = java.util.Set.of(
        "java.lang.Integer", "java.lang.Long", "java.lang.Double",
        "java.lang.Float", "java.lang.Boolean", "java.lang.Byte",
        "java.lang.Short", "java.lang.Character"
    );

    private static final String SUPPORTED_TYPES_MSG =
        "Supported: String, Multi<AgentEvent>, AgentSession, POJO classes, List<T>, Set<T>, Collection<T>.";

    private boolean validateReturnType(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        String methodName = method.getSimpleName().toString();
        String typeName = returnType.toString();

        if (returnType.getKind() == TypeKind.VOID) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@AgentService method '" + methodName + "' has unsupported return type 'void'. " + SUPPORTED_TYPES_MSG, method);
            return false;
        }

        if (returnType.getKind().isPrimitive()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@AgentService method '" + methodName + "' has unsupported return type '" + typeName + "'. " + SUPPORTED_TYPES_MSG, method);
            return false;
        }

        if (returnType.getKind() == TypeKind.ARRAY) {
            String componentType = ((ArrayType) returnType).getComponentType().toString();
            String componentSimple = simpleTypeName(componentType);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@AgentService method '" + methodName + "' has unsupported return type '" + typeName + "'. Use List<" + componentSimple + "> instead. " + SUPPORTED_TYPES_MSG, method);
            return false;
        }

        if (returnType instanceof DeclaredType dt) {
            String raw = dt.asElement().toString();

            if (BOXED_PRIMITIVES.contains(raw)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@AgentService method '" + methodName + "' has unsupported return type '" + typeName + "'. Use a wrapper record instead. " + SUPPORTED_TYPES_MSG, method);
                return false;
            }

            if (isCollection(returnType) && dt.getTypeArguments().isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@AgentService method '" + methodName + "' has unsupported return type '" + typeName + "'. Collection types require a type argument. " + SUPPORTED_TYPES_MSG, method);
                return false;
            }
        }

        return true;
    }

    private boolean isMultiAgentEvent(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            String raw = dt.asElement().toString();
            return raw.equals("io.smallrye.mutiny.Multi");
        }
        return false;
    }

    private boolean isAgentSession(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            String raw = dt.asElement().toString();
            return raw.equals("io.casehub.platform.agent.AgentSession");
        }
        return false;
    }

    private boolean isString(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            String raw = dt.asElement().toString();
            return raw.equals("java.lang.String");
        }
        return false;
    }

    private boolean isCollection(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            String raw = dt.asElement().toString();
            return raw.equals("java.util.List")
                || raw.equals("java.util.Set")
                || raw.equals("java.util.Collection");
        }
        return false;
    }

    private TypeMirror collectionElementType(TypeMirror type) {
        if (type instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
            return dt.getTypeArguments().get(0);
        }
        return null;
    }

    private String collectionKind(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            String raw = dt.asElement().toString();
            if (raw.equals("java.util.Set")) return "Set";
            if (raw.equals("java.util.Collection")) return "Collection";
        }
        return "List";
    }

    private static String schemaFieldName(String qualifiedType) {
        String simple = qualifiedType.contains(".") ? qualifiedType.substring(qualifiedType.lastIndexOf('.') + 1) : qualifiedType;
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1) + "Schema";
    }

    private static String simpleTypeName(String qualifiedType) {
        return qualifiedType.contains(".") ? qualifiedType.substring(qualifiedType.lastIndexOf('.') + 1) : qualifiedType;
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
