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
            out.println();

            out.println("public class " + proxyName + " implements " + simpleName + " {");
            out.println();
            out.println("    private final AgentProvider agentProvider;");
            out.println("    private final AgentResolver agentResolver;");
            out.println();
            out.println("    public " + proxyName + "(AgentProvider agentProvider, AgentResolver agentResolver) {");
            out.println("        this.agentProvider = agentProvider;");
            out.println("        this.agentResolver = agentResolver;");
            out.println("    }");

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
            out.println("        AgentInvocationResult result = agentProvider.invokeBlocking(config, " + timeoutExpr + ");");
            out.println("        return result.text();");
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

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
