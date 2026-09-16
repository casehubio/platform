package io.casehub.platform.simulation.restclient.generator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("*")
public class RestClientSimulationProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private static final DotName REGISTER_REST_CLIENT =
            DotName.createSimple("org.eclipse.microprofile.rest.client.inject.RegisterRestClient");
    private static final DotName SIMULATION_ELIGIBLE =
            DotName.createSimple("io.casehub.platform.simulation.SimulationEligible");
    private static final DotName PATH = DotName.createSimple("jakarta.ws.rs.Path");
    private static final DotName PATH_PARAM = DotName.createSimple("jakarta.ws.rs.PathParam");
    private static final DotName QUERY_PARAM = DotName.createSimple("jakarta.ws.rs.QueryParam");
    private static final DotName HEADER_PARAM = DotName.createSimple("jakarta.ws.rs.HeaderParam");

    private static final Set<DotName> HTTP_METHODS = Set.of(
            DotName.createSimple("jakarta.ws.rs.GET"),
            DotName.createSimple("jakarta.ws.rs.POST"),
            DotName.createSimple("jakarta.ws.rs.PUT"),
            DotName.createSimple("jakarta.ws.rs.DELETE"),
            DotName.createSimple("jakarta.ws.rs.PATCH"));

    private static final String GENERATED_PACKAGE = "io.casehub.platform.simulation.generated";

    private boolean processed = false;

    public record GeneratedSource(String className, String sourceCode) {}

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (processed || roundEnv.processingOver()) {
            return false;
        }
        processed = true;

        final IndexView index = loadCombinedIndex();
        if (index == null) {
            return false;
        }

        final List<GeneratedSource> sources = generateFromIndex(index);
        for (final GeneratedSource source : sources) {
            writeSourceFile(source);
        }
        return false;
    }

    List<GeneratedSource> generateFromIndex(final IndexView index) {
        final List<GeneratedSource> results = new ArrayList<>();

        for (final AnnotationInstance ann : index.getAnnotations(REGISTER_REST_CLIENT)) {
            if (ann.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            final ClassInfo classInfo = ann.target().asClass();
            if (!java.lang.reflect.Modifier.isInterface(classInfo.flags())) continue;
            if (classInfo.hasAnnotation(SIMULATION_ELIGIBLE)) continue;

            final AnnotationValue configKeyVal = ann.value("configKey");
            final String spiName = (configKeyVal != null && !configKeyVal.asString().isEmpty())
                    ? configKeyVal.asString()
                    : toKebabCase(classInfo.simpleName());

            final String classPath = resolveClassPath(classInfo);
            final String decoratorName = "Simulated" + classInfo.simpleName();
            final String fqcn = GENERATED_PACKAGE + "." + decoratorName;
            final String source = generateDecoratorSource(classInfo, spiName, decoratorName, classPath);
            results.add(new GeneratedSource(fqcn, source));
        }

        return results;
    }

    private String resolveClassPath(final ClassInfo classInfo) {
        final AnnotationInstance pathAnn = classInfo.classAnnotation(PATH);
        return pathAnn != null ? pathAnn.value().asString() : "";
    }

    private String generateDecoratorSource(final ClassInfo spiClass, final String spiName,
                                           final String decoratorName, final String classPath) {
        final StringBuilder sb = new StringBuilder();
        final String spiSimpleName = spiClass.simpleName();
        final String spiFullName = spiClass.name().toString();

        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");

        sb.append("import jakarta.decorator.Decorator;\n");
        sb.append("import jakarta.decorator.Delegate;\n");
        sb.append("import jakarta.annotation.Priority;\n");
        sb.append("import jakarta.inject.Inject;\n");
        sb.append("import org.eclipse.microprofile.rest.client.inject.RestClient;\n");
        sb.append("import io.casehub.platform.simulation.SimulationRuntime;\n");
        sb.append("import io.casehub.platform.simulation.SimulationStrategy;\n");
        sb.append("import io.casehub.platform.simulation.RestInvocation;\n");
        sb.append("import io.casehub.platform.api.identity.CurrentPrincipal;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.LinkedHashMap;\n");
        sb.append("import ").append(spiFullName).append(";\n");

        final Set<String> paramImports = collectParameterImports(spiClass);
        for (final String imp : paramImports) {
            sb.append("import ").append(imp).append(";\n");
        }

        sb.append("\n");
        sb.append("// GENERATED by RestClientSimulationProcessor — do not edit\n");
        sb.append("@Decorator\n");
        sb.append("@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 200)\n");
        sb.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n");
        sb.append("public class ").append(decoratorName);
        sb.append(" implements ").append(spiSimpleName).append(" {\n\n");

        sb.append("    @Inject @Delegate @RestClient ").append(spiSimpleName).append(" delegate;\n");
        sb.append("    @Inject SimulationRuntime simulation;\n");
        sb.append("    @Inject CurrentPrincipal currentPrincipal;\n\n");

        for (final MethodInfo method : spiClass.methods()) {
            if (method.isSynthetic()) continue;
            if (java.lang.reflect.Modifier.isAbstract(method.flags())) {
                generateSimulatedMethod(sb, method, spiName, classPath);
            } else {
                generateDelegatingMethod(sb, method);
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void generateSimulatedMethod(final StringBuilder sb, final MethodInfo method,
                                         final String spiName, final String classPath) {
        final String returnType = typeToJava(method.returnType());
        final boolean isVoid = method.returnType().kind() == Type.Kind.VOID;
        final String qualifiedName = spiName + "." + method.name();

        final String httpMethod = resolveHttpMethod(method);
        final String methodPath = resolveMethodPath(method);
        final String fullPath = classPath + methodPath;

        final StringBuilder params = new StringBuilder();
        final StringBuilder args = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) {
                params.append(", ");
                args.append(", ");
            }
            final String paramType = typeToJava(method.parameterTypes().get(i));
            final String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;
            params.append(paramType).append(" ").append(paramName);
            args.append(paramName);
        }

        sb.append("    @Override\n");
        sb.append("    public ").append(returnType).append(" ").append(method.name());
        sb.append("(").append(params).append(") {\n");

        sb.append("        String qualifiedName = \"").append(qualifiedName).append("\";\n");

        sb.append("        Map<String, Object> paramsMap = new LinkedHashMap<>();\n");
        String bodyParamName = null;
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            final String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;
            final AnnotationInstance pathParam = findParamAnnotation(method, i, PATH_PARAM);
            final AnnotationInstance queryParam = findParamAnnotation(method, i, QUERY_PARAM);
            final AnnotationInstance headerParam = findParamAnnotation(method, i, HEADER_PARAM);

            if (pathParam != null) {
                final String key = pathParam.value().asString();
                sb.append("        paramsMap.put(\"").append(key).append("\", ").append(paramName).append(");\n");
            } else if (queryParam != null) {
                final String key = queryParam.value().asString();
                sb.append("        paramsMap.put(\"").append(key).append("\", ").append(paramName).append(");\n");
            } else if (headerParam != null) {
                final String key = headerParam.value().asString();
                sb.append("        paramsMap.put(\"").append(key).append("\", ").append(paramName).append(");\n");
            } else {
                bodyParamName = paramName;
            }
        }

        sb.append("        RestInvocation input = new RestInvocation(\"")
                .append(spiName).append("\", \"").append(method.name()).append("\", ");
        if (httpMethod != null) {
            sb.append("\"").append(httpMethod).append("\"");
        } else {
            sb.append("null");
        }
        sb.append(", \"").append(fullPath).append("\", paramsMap, ");
        sb.append(bodyParamName != null ? bodyParamName : "null");
        sb.append(");\n");

        sb.append("        java.util.Optional<SimulationStrategy<Object, Object>> strategy = simulation.strategyFor(qualifiedName);\n");
        sb.append("        if (strategy.isPresent() && strategy.get().canResolve(input)) {\n");
        if (isVoid) {
            sb.append("            strategy.get().resolve(input);\n");
            sb.append("            return;\n");
        } else {
            sb.append("            return (").append(returnType).append(") strategy.get().resolve(input);\n");
        }
        sb.append("        }\n");

        if (isVoid) {
            sb.append("        delegate.").append(method.name()).append("(").append(args).append(");\n");
            sb.append("        if (simulation.captureEnabled(qualifiedName)) {\n");
            sb.append("            simulation.capture(qualifiedName, currentPrincipal.tenancyId(), input, null);\n");
            sb.append("        }\n");
        } else {
            sb.append("        ").append(returnType).append(" result = delegate.")
                    .append(method.name()).append("(").append(args).append(");\n");
            sb.append("        if (simulation.captureEnabled(qualifiedName)) {\n");
            sb.append("            simulation.capture(qualifiedName, currentPrincipal.tenancyId(), input, result);\n");
            sb.append("        }\n");
            sb.append("        return result;\n");
        }

        sb.append("    }\n\n");
    }

    private void generateDelegatingMethod(final StringBuilder sb, final MethodInfo method) {
        final String returnType = typeToJava(method.returnType());
        final boolean isVoid = method.returnType().kind() == Type.Kind.VOID;

        final StringBuilder params = new StringBuilder();
        final StringBuilder args = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) {
                params.append(", ");
                args.append(", ");
            }
            final String paramType = typeToJava(method.parameterTypes().get(i));
            final String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;
            params.append(paramType).append(" ").append(paramName);
            args.append(paramName);
        }

        sb.append("    @Override\n");
        sb.append("    public ").append(returnType).append(" ").append(method.name());
        sb.append("(").append(params).append(") {\n");
        if (isVoid) {
            sb.append("        delegate.").append(method.name()).append("(").append(args).append(");\n");
        } else {
            sb.append("        return delegate.").append(method.name()).append("(").append(args).append(");\n");
        }
        sb.append("    }\n\n");
    }

    private String resolveHttpMethod(final MethodInfo method) {
        for (final DotName httpDot : HTTP_METHODS) {
            if (method.hasAnnotation(httpDot)) {
                final String fqn = httpDot.toString();
                return fqn.substring(fqn.lastIndexOf('.') + 1);
            }
        }
        return null;
    }

    private String resolveMethodPath(final MethodInfo method) {
        final AnnotationInstance pathAnn = method.annotation(PATH);
        return pathAnn != null ? pathAnn.value().asString() : "";
    }

    private AnnotationInstance findParamAnnotation(final MethodInfo method, final int paramIndex,
                                                   final DotName annName) {
        for (final AnnotationInstance ann : method.annotations()) {
            if (ann.name().equals(annName)
                    && ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER
                    && ann.target().asMethodParameter().position() == paramIndex) {
                return ann;
            }
        }
        return null;
    }

    private Set<String> collectParameterImports(final ClassInfo spiClass) {
        final Set<String> imports = new HashSet<>();
        for (final MethodInfo method : spiClass.methods()) {
            if (method.isSynthetic()) continue;
            for (final Type paramType : method.parameterTypes()) {
                addTypeImport(imports, paramType);
            }
            if (method.returnType().kind() != Type.Kind.VOID) {
                addTypeImport(imports, method.returnType());
            }
        }
        return imports;
    }

    private void addTypeImport(final Set<String> imports, final Type type) {
        switch (type.kind()) {
            case CLASS -> {
                final String name = type.name().toString();
                if (!name.startsWith("java.lang.") || name.indexOf('.', 10) > 0) {
                    imports.add(name);
                }
            }
            case PARAMETERIZED_TYPE -> {
                imports.add(type.asParameterizedType().name().toString());
                for (final Type arg : type.asParameterizedType().arguments()) {
                    addTypeImport(imports, arg);
                }
            }
            case ARRAY -> addTypeImport(imports, type.asArrayType().constituent());
            default -> {}
        }
    }

    static String toKebabCase(final String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) return camelCase;
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            final char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    final boolean prevUpper = Character.isUpperCase(camelCase.charAt(i - 1));
                    final boolean nextLower = (i + 1 < camelCase.length())
                            && Character.isLowerCase(camelCase.charAt(i + 1));
                    if (!prevUpper || nextLower) {
                        result.append('-');
                    }
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String typeToJava(final Type type) {
        return switch (type.kind()) {
            case VOID -> "void";
            case PRIMITIVE -> type.asPrimitiveType().primitive().name().toLowerCase();
            case CLASS -> type.asClassType().name().local();
            case PARAMETERIZED_TYPE -> {
                final StringBuilder sb = new StringBuilder(type.asParameterizedType().name().local());
                sb.append("<");
                final List<Type> args = type.asParameterizedType().arguments();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(typeToJava(args.get(i)));
                }
                sb.append(">");
                yield sb.toString();
            }
            case ARRAY -> typeToJava(type.asArrayType().constituent()) + "[]";
            default -> type.name().toString();
        };
    }

    private IndexView loadCombinedIndex() {
        final List<IndexView> indexes = new ArrayList<>();
        try {
            final Enumeration<URL> resources =
                    getClass().getClassLoader().getResources("META-INF/jandex.idx");
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                try (InputStream is = url.openStream()) {
                    indexes.add(new IndexReader(is).read());
                }
            }
        } catch (IOException e) {
            if (processingEnv != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "REST client simulation generator: failed to load Jandex indexes: " + e.getMessage());
            }
            return null;
        }
        return indexes.isEmpty() ? null : CompositeIndex.create(indexes);
    }

    private void writeSourceFile(final GeneratedSource source) {
        if (processingEnv == null) return;
        try {
            var file = processingEnv.getFiler().createSourceFile(source.className());
            try (Writer writer = file.openWriter()) {
                writer.write(source.sourceCode());
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "REST client simulation generator: failed to write " + source.className() + ": " + e.getMessage());
        }
    }
}
