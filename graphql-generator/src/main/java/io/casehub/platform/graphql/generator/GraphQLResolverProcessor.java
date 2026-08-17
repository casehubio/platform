package io.casehub.platform.graphql.generator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Type;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class GraphQLResolverProcessor extends AbstractProcessor {

    private static final DotName MCP_DOMAIN = DotName.createSimple("io.casehub.platform.api.mcp.McpDomain");
    private static final DotName PLATFORM_QUERY = DotName.createSimple("io.casehub.platform.api.mcp.PlatformQuery");
    private static final DotName PLATFORM_MUTATION = DotName.createSimple("io.casehub.platform.api.mcp.PlatformMutation");
    private static final DotName GRAPHQL_API = DotName.createSimple("org.eclipse.microprofile.graphql.GraphQLApi");
    private static final DotName QUERY = DotName.createSimple("org.eclipse.microprofile.graphql.Query");
    private static final DotName MUTATION = DotName.createSimple("org.eclipse.microprofile.graphql.Mutation");

    private boolean processed = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (processed || roundEnv.processingOver()) {
            return false;
        }
        processed = true;

        IndexView index = loadCombinedIndex();
        if (index == null) {
            return false;
        }

        Set<String> handWrittenMethods = scanHandWrittenMethods(index);
        Map<String, DomainOperations> domains = scanAnnotatedInterfaces(index);

        if (domains.isEmpty()) {
            return false;
        }

        for (var entry : domains.entrySet()) {
            generateResolverSource(entry.getKey(), entry.getValue(), handWrittenMethods);
        }

        return false;
    }

    private IndexView loadCombinedIndex() {
        List<IndexView> indexes = new ArrayList<>();
        try {
            ClassLoader cl = getClass().getClassLoader();
            Enumeration<URL> resources = cl.getResources("META-INF/jandex.idx");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream is = url.openStream()) {
                    indexes.add(new IndexReader(is).read());
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "GraphQL generator: failed to read Jandex indexes: " + e.getMessage());
            return null;
        }

        if (indexes.isEmpty()) {
            return null;
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "GraphQL generator: loaded " + indexes.size() + " Jandex index(es)");
        return CompositeIndex.create(indexes);
    }

    private Set<String> scanHandWrittenMethods(IndexView index) {
        Set<String> methods = new HashSet<>();
        for (AnnotationInstance ann : index.getAnnotations(GRAPHQL_API)) {
            if (ann.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo classInfo = ann.target().asClass();
            AnnotationInstance mcpDomain = classInfo.annotation(MCP_DOMAIN);
            if (mcpDomain == null) continue;
            String domain = mcpDomain.value().asString();
            for (MethodInfo method : classInfo.methods()) {
                if (method.hasAnnotation(QUERY) || method.hasAnnotation(MUTATION)) {
                    methods.add(domain + ":" + method.name());
                }
            }
        }
        return methods;
    }

    private Map<String, DomainOperations> scanAnnotatedInterfaces(IndexView index) {
        Map<String, DomainOperations> domains = new HashMap<>();

        for (AnnotationInstance ann : index.getAnnotations(MCP_DOMAIN)) {
            if (ann.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo classInfo = ann.target().asClass();
            if (!java.lang.reflect.Modifier.isInterface(classInfo.flags())) continue;

            String domain = ann.value().asString();
            DomainOperations ops = domains.computeIfAbsent(domain, DomainOperations::new);

            for (MethodInfo method : classInfo.methods()) {
                AnnotationInstance queryAnn = method.annotation(PLATFORM_QUERY);
                AnnotationInstance mutAnn = method.annotation(PLATFORM_MUTATION);

                if (queryAnn != null) {
                    String desc = queryAnn.value() != null ? queryAnn.value().asString() : "";
                    ops.operations.add(new OperationInfo(method, classInfo, OperationType.QUERY, desc));
                } else if (mutAnn != null) {
                    String desc = mutAnn.value() != null ? mutAnn.value().asString() : "";
                    ops.operations.add(new OperationInfo(method, classInfo, OperationType.MUTATION, desc));
                }
            }
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "GraphQL generator: scanned " + domains.size() + " domain(s)");
        return domains;
    }

    private void generateResolverSource(String domain, DomainOperations ops,
                                        Set<String> handWrittenMethods) {
        String className = "Generated" + capitalize(domain) + "Resolver";
        String packageName = "io.casehub.platform.graphql.generated";
        String fqcn = packageName + "." + className;

        Set<String> spiImports = new HashSet<>();
        List<OperationInfo> toGenerate = new ArrayList<>();

        for (OperationInfo op : ops.operations) {
            String skipKey = domain + ":" + op.method.name();
            if (handWrittenMethods.contains(skipKey)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        "GraphQL generator: skipping " + skipKey + " — hand-written resolver exists");
                continue;
            }
            toGenerate.add(op);
            spiImports.add(op.declaringClass.name().toString());
        }

        if (toGenerate.isEmpty()) {
            return;
        }

        try {
            JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(fqcn);
            try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
                out.println("package " + packageName + ";");
                out.println();
                out.println("import jakarta.enterprise.context.ApplicationScoped;");
                out.println("import jakarta.inject.Inject;");
                out.println("import org.eclipse.microprofile.graphql.GraphQLApi;");
                out.println("import org.eclipse.microprofile.graphql.Query;");
                out.println("import org.eclipse.microprofile.graphql.Mutation;");
                out.println("import org.eclipse.microprofile.graphql.Description;");
                out.println("import io.casehub.platform.api.mcp.McpDomain;");

                Set<String> typeImports = collectTypeImports(toGenerate);
                for (String imp : typeImports) {
                    if (!imp.startsWith("java.lang.") && imp.contains(".")) {
                        out.println("import " + imp + ";");
                    }
                }
                for (String imp : spiImports) {
                    out.println("import " + imp + ";");
                }

                out.println();
                out.println("// GENERATED by GraphQLResolverProcessor — do not edit");
                out.println("@GraphQLApi");
                out.println("@McpDomain(\"" + domain + "\")");
                out.println("@ApplicationScoped");
                out.println("public class " + className + " {");
                out.println();

                Set<String> injectedFields = new HashSet<>();
                for (OperationInfo op : toGenerate) {
                    String fieldName = decapitalize(op.declaringClass.simpleName());
                    if (injectedFields.add(fieldName)) {
                        out.println("    @Inject");
                        out.println("    " + op.declaringClass.simpleName() + " " + fieldName + ";");
                        out.println();
                    }
                }

                for (OperationInfo op : toGenerate) {
                    generateMethod(out, op);
                }

                out.println("}");
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "GraphQL generator: generated " + fqcn);

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "GraphQL generator: failed to write " + fqcn + ": " + e.getMessage());
        }
    }

    private void generateMethod(PrintWriter out, OperationInfo op) {
        MethodInfo method = op.method;
        String annotation = op.type == OperationType.QUERY ? "@Query" : "@Mutation";

        out.println("    " + annotation);
        if (!op.description.isEmpty()) {
            out.println("    @Description(\"" + escapeJavaString(op.description) + "\")");
        }

        String returnType = typeToJava(method.returnType());
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) params.append(", ");
            params.append(typeToJava(method.parameterTypes().get(i)));
            params.append(" ");
            params.append(method.parameterName(i) != null ? method.parameterName(i) : "arg" + i);
        }

        out.println("    public " + returnType + " " + method.name() + "(" + params + ") {");

        String fieldName = decapitalize(op.declaringClass.simpleName());
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) args.append(", ");
            args.append(method.parameterName(i) != null ? method.parameterName(i) : "arg" + i);
        }

        if (method.returnType().kind() == Type.Kind.VOID) {
            out.println("        " + fieldName + "." + method.name() + "(" + args + ");");
        } else {
            out.println("        return " + fieldName + "." + method.name() + "(" + args + ");");
        }

        out.println("    }");
        out.println();
    }

    private Set<String> collectTypeImports(List<OperationInfo> operations) {
        Set<String> imports = new HashSet<>();
        for (OperationInfo op : operations) {
            addTypeImport(imports, op.method.returnType());
            for (Type paramType : op.method.parameterTypes()) {
                addTypeImport(imports, paramType);
            }
        }
        return imports;
    }

    private void addTypeImport(Set<String> imports, Type type) {
        switch (type.kind()) {
            case CLASS -> imports.add(type.name().toString());
            case PARAMETERIZED_TYPE -> {
                imports.add(type.asParameterizedType().name().toString());
                for (Type arg : type.asParameterizedType().arguments()) {
                    addTypeImport(imports, arg);
                }
            }
            case ARRAY -> addTypeImport(imports, type.asArrayType().constituent());
            default -> {}
        }
    }

    private String typeToJava(Type type) {
        return switch (type.kind()) {
            case VOID -> "void";
            case PRIMITIVE -> type.asPrimitiveType().primitive().name().toLowerCase();
            case CLASS -> type.asClassType().name().local();
            case PARAMETERIZED_TYPE -> {
                StringBuilder sb = new StringBuilder(type.asParameterizedType().name().local());
                sb.append("<");
                List<Type> args = type.asParameterizedType().arguments();
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

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String escapeJavaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    enum OperationType { QUERY, MUTATION }

    static class DomainOperations {
        final String domain;
        final List<OperationInfo> operations = new ArrayList<>();
        DomainOperations(String domain) { this.domain = domain; }
    }

    static class OperationInfo {
        final MethodInfo method;
        final ClassInfo declaringClass;
        final OperationType type;
        final String description;
        OperationInfo(MethodInfo method, ClassInfo declaringClass, OperationType type, String description) {
            this.method = method;
            this.declaringClass = declaringClass;
            this.type = type;
            this.description = description;
        }
    }
}
