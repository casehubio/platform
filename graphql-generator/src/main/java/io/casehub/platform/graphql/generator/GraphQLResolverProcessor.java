package io.casehub.platform.graphql.generator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
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
@javax.annotation.processing.SupportedOptions({"generateGraphQL", "generateRest", "domainFilter"})
public class GraphQLResolverProcessor extends AbstractProcessor {

    private static final DotName MCP_DOMAIN = DotName.createSimple("io.casehub.platform.api.mcp.McpDomain");
    private static final DotName PLATFORM_QUERY = DotName.createSimple("io.casehub.platform.api.mcp.PlatformQuery");
    private static final DotName PLATFORM_MUTATION = DotName.createSimple("io.casehub.platform.api.mcp.PlatformMutation");
    private static final DotName GRAPHQL_API = DotName.createSimple("org.eclipse.microprofile.graphql.GraphQLApi");
    private static final DotName QUERY = DotName.createSimple("org.eclipse.microprofile.graphql.Query");
    private static final DotName MUTATION = DotName.createSimple("org.eclipse.microprofile.graphql.Mutation");
    private static final DotName REST_METHOD_ANN = DotName.createSimple("io.casehub.platform.api.mcp.RestMethod");
    private static final DotName PATH_PARAM_ANN = DotName.createSimple("io.casehub.platform.api.mcp.PathParam");
    private static final DotName PATH = DotName.createSimple("jakarta.ws.rs.Path");
    private static final DotName JAX_GET = DotName.createSimple("jakarta.ws.rs.GET");
    private static final DotName JAX_POST = DotName.createSimple("jakarta.ws.rs.POST");
    private static final DotName JAX_PUT = DotName.createSimple("jakarta.ws.rs.PUT");
    private static final DotName JAX_DELETE = DotName.createSimple("jakarta.ws.rs.DELETE");
    private static final DotName JAX_PATCH = DotName.createSimple("jakarta.ws.rs.PATCH");
    private static final DotName REST_PATH_ANN = DotName.createSimple("io.casehub.platform.api.mcp.RestPath");


    private boolean processed = false;
    private IndexView jandexIndex;

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
        this.jandexIndex = index;

        Set<String> graphqlSkipMethods = scanHandWrittenGraphQLMethods(index);
        Set<String> restSkipMethods = scanHandWrittenRestMethods(index);
        Map<String, DomainOperations> domains = scanAnnotatedInterfaces(index);

        if (domains.isEmpty()) {
            return false;
        }

        boolean generateGraphQL = !"false".equals(processingEnv.getOptions().get("generateGraphQL"));
        boolean generateRest = !"false".equals(processingEnv.getOptions().get("generateRest"));
        String domainFilter = processingEnv.getOptions().get("domainFilter");
        Set<String> allowedDomains = domainFilter != null
            ? java.util.Arrays.stream(domainFilter.split(",")).map(String::trim).collect(java.util.stream.Collectors.toSet())
            : null;

        for (var entry : domains.entrySet()) {
            if (allowedDomains != null && !allowedDomains.contains(entry.getKey())) {
                continue;
            }
            if (generateGraphQL) {
                generateResolverSource(entry.getKey(), entry.getValue(), graphqlSkipMethods);
            }
            if (generateRest) {
                generateRestResourceSource(entry.getKey(), entry.getValue(), restSkipMethods);
            }
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

    private Set<String> scanHandWrittenGraphQLMethods(IndexView index) {
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

    private Set<String> scanHandWrittenRestMethods(IndexView index) {
        Set<String> methods = new HashSet<>();
        for (AnnotationInstance pathAnn : index.getAnnotations(PATH)) {
            if (pathAnn.target().kind() != AnnotationTarget.Kind.CLASS) {continue;}
            ClassInfo          classInfo = pathAnn.target().asClass();
            AnnotationInstance mcpDomain = classInfo.annotation(MCP_DOMAIN);
            if (mcpDomain == null) {continue;}
            String domain = mcpDomain.value().asString();
            for (MethodInfo method : classInfo.methods()) {
                if (method.hasAnnotation(JAX_GET) || method.hasAnnotation(JAX_POST)
                    || method.hasAnnotation(JAX_PUT) || method.hasAnnotation(JAX_DELETE)
                    || method.hasAnnotation(JAX_PATCH)) {
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

                if (queryAnn != null || mutAnn != null) {
                    OperationType opType = queryAnn != null ? OperationType.QUERY : OperationType.MUTATION;
                    String desc = queryAnn != null
                        ? (queryAnn.value() != null ? queryAnn.value().asString() : "")
                        : (mutAnn.value() != null ? mutAnn.value().asString() : "");
                    String restMethodOverride = null;
                    AnnotationInstance restMethodAnn = method.annotation(REST_METHOD_ANN);
                    if (restMethodAnn != null && restMethodAnn.value() != null) {
                        restMethodOverride = restMethodAnn.value().asEnum();
                    }
                    String restPathOverride = null;
                    AnnotationInstance restPathAnn = method.annotation(REST_PATH_ANN);
                    if (restPathAnn != null && restPathAnn.value() != null) {
                        restPathOverride = restPathAnn.value().asString();
                    }
                    ops.operations.add(new OperationInfo(method, classInfo, opType, desc, restMethodOverride, restPathOverride));
                }
            }
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "GraphQL generator: scanned " + domains.size() + " domain(s)");
        return domains;
    }

    private void generateResolverSource(String domain, DomainOperations ops,
                                        Set<String> handWrittenMethods) {
        String className = "Generated" + toPascalCase(domain) + "Resolver";
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

    private void generateRestResourceSource(String domain, DomainOperations ops,
                                            Set<String> handWrittenMethods) {
        String className   = "Generated" + toPascalCase(domain) + "Resource";
        String packageName = "io.casehub.platform.rest.generated";
        String fqcn        = packageName + "." + className;

        Set<String>         spiImports = new HashSet<>();
        List<OperationInfo> toGenerate = new ArrayList<>();

        for (OperationInfo op : ops.operations) {
            String skipKey = domain + ":" + op.method.name();
            if (handWrittenMethods.contains(skipKey)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        "REST generator: skipping " + skipKey + " — hand-written REST resource exists");
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
                out.println("import jakarta.ws.rs.Consumes;");
                out.println("import jakarta.ws.rs.DELETE;");
                out.println("import jakarta.ws.rs.GET;");
                out.println("import jakarta.ws.rs.PATCH;");
                out.println("import jakarta.ws.rs.POST;");
                out.println("import jakarta.ws.rs.PUT;");
                out.println("import jakarta.ws.rs.Path;");
                out.println("import jakarta.ws.rs.QueryParam;");
                out.println("import jakarta.ws.rs.Produces;");
                out.println("import jakarta.ws.rs.core.MediaType;");
                out.println("import jakarta.ws.rs.core.Response;");
                out.println("import io.smallrye.common.annotation.RunOnVirtualThread;");

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
                out.println("@Path(\"/api/" + domain + "\")");
                out.println("@Produces(MediaType.APPLICATION_JSON)");
                out.println("@RunOnVirtualThread");
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
                    generateRestMethod(out, op);
                }

                out.println("}");
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                                                     "REST generator: generated " + fqcn);

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                                     "REST generator: failed to write " + fqcn + ": " + e.getMessage());
        }
    }

    private void generateRestMethod(PrintWriter out, OperationInfo op) {
        MethodInfo method = op.method;
        String httpVerb = resolveHttpVerb(op.type, op.restMethodOverride);
        boolean isBodyVerb = httpVerb.equals("POST") || httpVerb.equals("PUT") || httpVerb.equals("PATCH");

        List<String> pathParams = new ArrayList<>();
        Set<Integer> pathParamPositions = new HashSet<>();
        int bodyParamIndex = -1;
        int complexCount = 0;

        for (int i = 0; i < method.parameterTypes().size(); i++) {
            AnnotationInstance ppAnn = findParameterAnnotation(method, i, PATH_PARAM_ANN);
            if (ppAnn != null) {
                String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;
                String pathName = (ppAnn.value() != null && !ppAnn.value().asString().isEmpty())
                    ? ppAnn.value().asString() : paramName;
                pathParams.add(pathName);
                pathParamPositions.add(i);
            } else if (isBodyVerb
                       && method.parameterTypes().get(i).kind() != Type.Kind.PRIMITIVE
                       && !isSimpleType(method.parameterTypes().get(i).name().toString(), jandexIndex)) {
                complexCount++;
                if (bodyParamIndex < 0) {
                    bodyParamIndex = i;
                }
            }
        }

        if (complexCount > 1) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "REST generator: method '" + method.name() + "' on domain '"
                + op.declaringClass.simpleName() + "' has " + complexCount
                + " complex parameters. Wrap them in a single request DTO"
                + " or annotate path parameters with @PathParam.");
            return;
        }

        boolean hasBody = bodyParamIndex >= 0;

        StringBuilder pathSuffix = new StringBuilder();
        pathSuffix.append("/").append(resolveRestPath(op.restPathOverride, method.name()));
        for (String pp : pathParams) {
            pathSuffix.append("/{").append(pp).append("}");
        }

        out.println("    @" + httpVerb);
        out.println("    @Path(\"" + pathSuffix + "\")");
        if (hasBody) {
            out.println("    @Consumes(MediaType.APPLICATION_JSON)");
        }

        StringBuilder params = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) params.append(", ");
            String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;

            if (pathParamPositions.contains(i)) {
                AnnotationInstance ppAnn = findParameterAnnotation(method, i, PATH_PARAM_ANN);
                String pathName = (ppAnn != null && ppAnn.value() != null && !ppAnn.value().asString().isEmpty())
                    ? ppAnn.value().asString() : paramName;
                params.append("@jakarta.ws.rs.PathParam(\"").append(pathName).append("\") ");
            } else if (i == bodyParamIndex) {
                params.append("@jakarta.validation.Valid ");
            } else {
                params.append("@QueryParam(\"").append(paramName).append("\") ");
            }
            params.append(typeToJava(method.parameterTypes().get(i)));
            params.append(" ").append(paramName);
        }

        out.println("    public Response " + method.name() + "(" + params + ") {");

        String fieldName = decapitalize(op.declaringClass.simpleName());
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) args.append(", ");
            args.append(method.parameterName(i) != null ? method.parameterName(i) : "arg" + i);
        }

        String returnTypeStr = typeToJava(method.returnType());
        String delegateCall = fieldName + "." + method.name() + "(" + args + ")";
        String responseCode = generateResponseCode(returnTypeStr, delegateCall);
        out.println("        " + responseCode);

        out.println("    }");
        out.println();
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

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String escapeJavaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    private static AnnotationInstance findParameterAnnotation(MethodInfo method, int paramIndex, DotName annotationName) {
        for (AnnotationInstance ann : method.annotations()) {
            if (ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER
                && ann.target().asMethodParameter().position() == paramIndex
                && ann.name().equals(annotationName)) {
                return ann;
            }
        }
        return null;
    }

    static String toKebabCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {return camelCase;}
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    char prev = camelCase.charAt(i - 1);
                    if (Character.isLowerCase(prev) || Character.isDigit(prev)) {
                        sb.append('-');
                    } else if (Character.isUpperCase(prev) && i + 1 < camelCase.length()
                               && Character.isLowerCase(camelCase.charAt(i + 1))) {
                        sb.append('-');
                    }
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String toPascalCase(String kebab) {
        if (kebab == null || kebab.isEmpty()) {return kebab;}
        kebab = kebab.replace('/', '-');
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("-")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    static String resolveHttpVerb(OperationType type, String restMethodOverride) {
        if (restMethodOverride != null) {
            return restMethodOverride;
        }
        return type == OperationType.QUERY ? "GET" : "POST";
    }

    static String resolveRestPath(String restPathOverride, String methodName) {
        if (restPathOverride != null) {
            return restPathOverride;
        }
        return toKebabCase(methodName);
    }


    private static final Set<String> SIMPLE_TYPES = Set.of(
            "java.lang.String",
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Float", "java.lang.Double", "java.lang.Boolean", "java.lang.Character",
            "java.util.UUID"
                                                          );

    static boolean isSimpleType(String fqcn) {
        return isSimpleType(fqcn, null);
    }

    static boolean isSimpleType(String fqcn, IndexView index) {
        if (SIMPLE_TYPES.contains(fqcn)) {return true;}
        if (fqcn.startsWith("java.time.")) {return true;}
        if (index != null) {
            ClassInfo ci = index.getClassByName(fqcn);
            if (ci != null) {
                if (ci.isEnum()) {return true;}
                if (hasStaticStringMethod(ci, "fromString")) {return true;}
                if (!ci.isEnum() && hasStaticStringMethod(ci, "valueOf")) {return true;}
            }
        }
        return false;
    }

    private static boolean hasStaticStringMethod(ClassInfo ci, String methodName) {
        DotName stringType = DotName.createSimple("java.lang.String");
        for (MethodInfo m : ci.methods()) {
            if (m.name().equals(methodName)
                && java.lang.reflect.Modifier.isStatic(m.flags())
                && m.parameterTypes().size() == 1
                && m.parameterTypes().get(0).name().equals(stringType)) {
                return true;
            }
        }
        return false;
    }

    static String generateResponseCode(String returnType, String delegateCall) {
        if ("void".equals(returnType)) {
            return delegateCall + "; return Response.noContent().build();";
        }
        if (returnType.startsWith("Optional<")) {
            return "return " + delegateCall + ".map(v -> Response.ok(v).build()).orElse(Response.status(404).build());";
        }
        return "return Response.ok(" + delegateCall + ").build();";
    }


    enum OperationType { QUERY, MUTATION }

    static class DomainOperations {
        final String domain;
        final List<OperationInfo> operations = new ArrayList<>();
        DomainOperations(String domain) { this.domain = domain; }
    }

    static class OperationInfo {
        final MethodInfo    method;
        final ClassInfo     declaringClass;
        final OperationType type;
        final String        description;
        final String        restMethodOverride;
        final String        restPathOverride;

        OperationInfo(MethodInfo method, ClassInfo declaringClass, OperationType type, String description, String restMethodOverride, String restPathOverride) {
            this.method             = method;
            this.declaringClass     = declaringClass;
            this.type               = type;
            this.description        = description;
            this.restMethodOverride = restMethodOverride;
            this.restPathOverride   = restPathOverride;
        }
    }
}
