package io.casehub.platform.simulation.generator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes("*")
public class SimulationDecoratorProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private static final DotName SIMULATION_ELIGIBLE =
            DotName.createSimple("io.casehub.platform.simulation.SimulationEligible");

    private static final String GENERATED_PACKAGE = "io.casehub.platform.simulation.generated";

    private static final String LISTING_FILE = "META-INF/simulation-eligible.txt";

    private boolean processed = false;

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

        final Map<String, String> paramEntries = generateParameterEntries(index);
        if (!paramEntries.isEmpty()) {
            writeParameterProperties(paramEntries);
        }

        return false;
    }

    List<GeneratedSource> generateFromIndex(final IndexView index) {
        final List<GeneratedSource> results = new ArrayList<>();
        final Set<String> processedClasses = new HashSet<>();

        for (final AnnotationInstance ann : index.getAnnotations(SIMULATION_ELIGIBLE)) {
            if (ann.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            final ClassInfo classInfo = ann.target().asClass();
            if (!java.lang.reflect.Modifier.isInterface(classInfo.flags())) continue;

            processedClasses.add(classInfo.name().toString());

            final AnnotationValue nameVal = ann.value("name");
            final String spiName = (nameVal != null && !nameVal.asString().isEmpty())
                    ? nameVal.asString()
                    : toKebabCase(classInfo.simpleName());

            final String decoratorName = "Simulated" + classInfo.simpleName();
            final String fqcn = GENERATED_PACKAGE + "." + decoratorName;
            final String source = generateDecoratorSource(classInfo, spiName, decoratorName);

            results.add(new GeneratedSource(fqcn, source));

            final String qnName = GENERATED_PACKAGE + "." + classInfo.simpleName() + "QN";
            final String qnSource = generateQNSource(classInfo, spiName);
            results.add(new GeneratedSource(qnName, qnSource));
        }

        final Map<String, String> listingEntries = loadListingFile();
        for (final Map.Entry<String, String> entry : listingEntries.entrySet()) {
            final String className = entry.getKey();
            if (processedClasses.contains(className)) continue;

            ClassInfo classInfo = index.getClassByName(DotName.createSimple(className));
            if (classInfo == null) {
                classInfo = indexClassFromClasspath(className);
            }
            if (classInfo == null) {
                if (processingEnv != null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                            "Simulation generator: class " + className
                                    + " from listing file not found in Jandex index or classpath");
                }
                continue;
            }
            if (!java.lang.reflect.Modifier.isInterface(classInfo.flags())) continue;

            final String spiName = entry.getValue();
            final String decoratorName = "Simulated" + classInfo.simpleName();
            final String fqcn = GENERATED_PACKAGE + "." + decoratorName;
            final String source = generateDecoratorSource(classInfo, spiName, decoratorName);
            results.add(new GeneratedSource(fqcn, source));

            final String qnName = GENERATED_PACKAGE + "." + classInfo.simpleName() + "QN";
            final String qnSource = generateQNSource(classInfo, spiName);
            results.add(new GeneratedSource(qnName, qnSource));
        }

        return results;
    }

    private String generateDecoratorSource(final ClassInfo spiClass, final String spiName,
                                           final String decoratorName) {
        final StringBuilder sb = new StringBuilder();
        final String spiSimpleName = spiClass.simpleName();
        final String spiFullName = spiClass.name().toString();

        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");

        sb.append("import jakarta.decorator.Decorator;\n");
        sb.append("import jakarta.decorator.Delegate;\n");
        sb.append("import jakarta.annotation.Priority;\n");
        sb.append("import jakarta.inject.Inject;\n");
        sb.append("import io.casehub.platform.simulation.SimulationRuntime;\n");
        sb.append("import io.casehub.platform.simulation.SimulationStrategy;\n");
        sb.append("import io.casehub.platform.api.identity.CurrentPrincipal;\n");
        sb.append("import ").append(spiFullName).append(";\n");

        final Set<String> paramImports = collectParameterImports(spiClass);
        for (final String imp : paramImports) {
            sb.append("import ").append(imp).append(";\n");
        }

        sb.append("\n");
        sb.append("// GENERATED by SimulationDecoratorProcessor — do not edit\n");
        sb.append("@Decorator\n");
        sb.append("@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 200)\n");
        sb.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n");
        sb.append("public class ").append(decoratorName);
        sb.append(" implements ").append(spiSimpleName).append(" {\n\n");

        sb.append("    @Inject @Delegate ").append(spiSimpleName).append(" delegate;\n");
        sb.append("    @Inject SimulationRuntime simulation;\n");
        sb.append("    @Inject CurrentPrincipal currentPrincipal;\n\n");

        for (final MethodInfo method : spiClass.methods()) {
            if (method.isSynthetic()) continue;
            generateSimulatedMethod(sb, method, spiName);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateQNSource(final ClassInfo spiClass, final String spiName) {
        final StringBuilder sb          = new StringBuilder();
        final String        qnClassName = spiClass.simpleName() + "QN";

        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        sb.append("// GENERATED by SimulationDecoratorProcessor — do not edit\n");
        sb.append("public final class ").append(qnClassName).append(" {\n\n");

        final Set<String> emittedMethods = new HashSet<>();
        for (final MethodInfo method : spiClass.methods()) {
            if (method.isSynthetic()) continue;
            if (!emittedMethods.add(method.name())) continue;
            final String constName     = method.name().toUpperCase();
            final String qualifiedName = spiName + "." + method.name();
            sb.append("    public static final String ").append(constName)
              .append(" = \"").append(qualifiedName).append("\";\n");
        }

        sb.append("\n    private ").append(qnClassName).append("() {}\n");
        sb.append("}\n");
        return sb.toString();
    }


    private void generateSimulatedMethod(final StringBuilder sb, final MethodInfo method, final String spiName) {
        final String returnType = typeToJava(method.returnType());
        final boolean isVoid = method.returnType().kind() == Type.Kind.VOID;
        final String qualifiedName = spiName + "." + method.name();

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

        final String inputExpr;
        if (method.parameterTypes().isEmpty()) {
            inputExpr = "null";
        } else if (method.parameterTypes().size() == 1) {
            final String paramName = method.parameterName(0) != null ? method.parameterName(0) : "arg0";
            inputExpr = paramName;
        } else {
            inputExpr = "new Object[]{" + args + "}";
        }

        sb.append("    @Override\n");
        sb.append("    public ");
        if (!method.typeParameters().isEmpty()) {
            sb.append("<");
            for (int i = 0; i < method.typeParameters().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(method.typeParameters().get(i).identifier());
            }
            sb.append("> ");
        }
        sb.append(returnType).append(" ").append(method.name());
        sb.append("(").append(params).append(") {\n");

        sb.append("        String qualifiedName = \"").append(qualifiedName).append("\";\n");
        sb.append("        String __simTenancyId = null;\n");
        sb.append("        try { __simTenancyId = currentPrincipal.tenancyId(); } catch (Exception ignored) {}\n");
        sb.append("        java.util.Optional<SimulationStrategy<Object, Object>> strategy = simulation.strategyFor(qualifiedName);\n");

        sb.append("        if (strategy.isPresent() && strategy.get().canResolve(").append(inputExpr).append(")) {\n");
        if (isVoid) {
            sb.append("            strategy.get().resolve(").append(inputExpr).append(");\n");
            sb.append("            simulation.recordJournal(qualifiedName, __simTenancyId, ").append(inputExpr).append(", null, true);\n");
            sb.append("            return;\n");
        } else {
            sb.append("            Object simResult = strategy.get().resolve(").append(inputExpr).append(");\n");
            sb.append("            simulation.recordJournal(qualifiedName, __simTenancyId, ").append(inputExpr).append(", simResult, true);\n");
            sb.append("            return (").append(returnType).append(") simResult;\n");
        }
        sb.append("        }\n");

        if (isVoid) {
            sb.append("        delegate.").append(method.name()).append("(").append(args).append(");\n");
            sb.append("        simulation.recordJournal(qualifiedName, __simTenancyId, ").append(inputExpr).append(", null, false);\n");
            sb.append("        if (simulation.captureEnabled(qualifiedName)) {\n");
            sb.append("            simulation.capture(qualifiedName, currentPrincipal.tenancyId(), ").append(inputExpr).append(", null);\n");
            sb.append("        }\n");
        } else {
            sb.append("        ").append(returnType).append(" result = delegate.").append(method.name()).append("(").append(args).append(");\n");
            sb.append("        simulation.recordJournal(qualifiedName, __simTenancyId, ").append(inputExpr).append(", result, false);\n");
            sb.append("        if (simulation.captureEnabled(qualifiedName)) {\n");
            sb.append("            simulation.capture(qualifiedName, currentPrincipal.tenancyId(), ").append(inputExpr).append(", result);\n");
            sb.append("        }\n");
            sb.append("        return result;\n");
        }

        sb.append("    }\n\n");
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

    Map<String, String> generateParameterEntries(final IndexView index) {
        final Map<String, String> entries = new LinkedHashMap<>();
        final Set<String> processedClasses = new HashSet<>();

        for (final AnnotationInstance ann : index.getAnnotations(SIMULATION_ELIGIBLE)) {
            if (ann.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            final ClassInfo classInfo = ann.target().asClass();
            if (!java.lang.reflect.Modifier.isInterface(classInfo.flags())) continue;
            processedClasses.add(classInfo.name().toString());

            final AnnotationValue nameVal = ann.value("name");
            final String spiName = (nameVal != null && !nameVal.asString().isEmpty())
                    ? nameVal.asString() : toKebabCase(classInfo.simpleName());

            addParameterEntries(classInfo, spiName, entries);
        }

        final Map<String, String> listingEntries = loadListingFile();
        for (final Map.Entry<String, String> entry : listingEntries.entrySet()) {
            final String className = entry.getKey();
            if (processedClasses.contains(className)) continue;
            ClassInfo classInfo = index.getClassByName(DotName.createSimple(className));
            if (classInfo == null) classInfo = indexClassFromClasspath(className);
            if (classInfo == null || !java.lang.reflect.Modifier.isInterface(classInfo.flags())) continue;
            addParameterEntries(classInfo, entry.getValue(), entries);
        }

        return entries;
    }

    private void addParameterEntries(final ClassInfo classInfo, final String spiName,
                                     final Map<String, String> entries) {
        final Set<String> seen = new HashSet<>();
        for (final MethodInfo method : classInfo.methods()) {
            if (method.isSynthetic()) continue;
            if (!seen.add(method.name())) continue;
            final String qn = spiName + "." + method.name();
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < method.parameterTypes().size(); i++) {
                if (i > 0) sb.append(",");
                final String paramName = method.parameterName(i) != null
                        ? method.parameterName(i) : "arg" + i;
                sb.append(paramName).append(":").append(i);
            }
            entries.put(qn, sb.toString());
        }
    }

    private void writeParameterProperties(final Map<String, String> entries) {
        try {
            final javax.tools.FileObject fo = processingEnv.getFiler().createResource(
                    javax.tools.StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/simulation-parameters.properties");
            try (java.io.PrintWriter out = new java.io.PrintWriter(fo.openWriter())) {
                out.println("# Generated by SimulationDecoratorProcessor — do not edit");
                entries.forEach((qn, params) -> out.println(qn + "=" + params));
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Simulation generator: wrote simulation-parameters.properties ("
                    + entries.size() + " entries)");
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "Simulation generator: failed to write simulation-parameters.properties: "
                    + e.getMessage());
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
            case TYPE_VARIABLE -> type.asTypeVariable().identifier();
            case WILDCARD_TYPE -> {
                final var wt = type.asWildcardType();
                if (wt.extendsBound() != null && !wt.extendsBound().name().equals(DotName.createSimple("java.lang.Object"))) {
                    yield "? extends " + typeToJava(wt.extendsBound());
                } else if (wt.superBound() != null) {
                    yield "? super " + typeToJava(wt.superBound());
                } else {
                    yield "?";
                }
            }
            default -> type.name().toString();
        };
    }

    private Map<String, String> loadListingFile() {
        final Map<String, String> entries = new LinkedHashMap<>();

        if (processingEnv != null) {
            loadListingFromFiler(entries);
        }

        loadListingFromClasspath(entries);

        return entries;
    }

    private ClassInfo indexClassFromClasspath(final String className) {
        final String resourceName = className.replace('.', '/') + ".class";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) return null;
            final Indexer indexer = new Indexer();
            indexer.index(is);
            final IndexView onDemandIndex = indexer.complete();
            return onDemandIndex.getClassByName(DotName.createSimple(className));
        } catch (IOException e) {
            if (processingEnv != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "Simulation generator: failed to index " + className + ": " + e.getMessage());
            }
            return null;
        }
    }

    private void loadListingFromFiler(final Map<String, String> entries) {
        try {
            final javax.tools.FileObject fo = processingEnv.getFiler().getResource(
                    javax.tools.StandardLocation.CLASS_OUTPUT, "", LISTING_FILE);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fo.openInputStream()))) {
                parseListingLines(reader, entries);
            }
        } catch (IOException ignored) {
        }
    }

    private void loadListingFromClasspath(final Map<String, String> entries) {
        try {
            final ClassLoader cl = getClass().getClassLoader();
            final Enumeration<URL> resources = cl.getResources(LISTING_FILE);
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(url.openStream()))) {
                    parseListingLines(reader, entries);
                }
            }
        } catch (IOException e) {
            if (processingEnv != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "Simulation generator: failed to read listing file: " + e.getMessage());
            }
        }
    }

    private static void parseListingLines(final BufferedReader reader,
                                           final Map<String, String> entries) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            final int eq = line.indexOf('=');
            if (eq < 0) continue;
            entries.putIfAbsent(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
    }

    private IndexView loadCombinedIndex() {
        final List<IndexView> indexes = new ArrayList<>();
        try {
            final ClassLoader cl = getClass().getClassLoader();
            final Enumeration<URL> resources = cl.getResources("META-INF/jandex.idx");
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                try (InputStream is = url.openStream()) {
                    indexes.add(new IndexReader(is).read());
                }
            }
        } catch (IOException e) {
            if (processingEnv != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "Simulation generator: failed to read Jandex indexes: " + e.getMessage());
            }
            return null;
        }

        if (indexes.isEmpty()) {
            return null;
        }

        if (processingEnv != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Simulation generator: loaded " + indexes.size() + " Jandex index(es)");
        }
        return CompositeIndex.create(indexes);
    }

    private void writeSourceFile(final GeneratedSource source) {
        try {
            final JavaFileObject file = processingEnv.getFiler().createSourceFile(source.className());
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.print(source.sourceCode());
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Simulation generator: generated " + source.className());

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Simulation generator: failed to write " + source.className() + ": " + e.getMessage());
        }
    }

    record GeneratedSource(String className, String sourceCode) {}
}
