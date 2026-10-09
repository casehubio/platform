package io.casehub.platform.confirmation.generator;

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
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
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
public class ConfirmationDecoratorProcessor extends AbstractProcessor {

    private static final DotName REQUIRES_CONFIRMATION =
            DotName.createSimple("io.casehub.platform.api.confirmation.RequiresConfirmation");

    private static final String GENERATED_PACKAGE = "io.casehub.platform.confirmation.generated";

    private boolean processed = false;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

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

        List<GeneratedSource> sources = generateFromIndex(index);
        for (GeneratedSource source : sources) {
            writeSourceFile(source);
        }

        return false;
    }

    List<GeneratedSource> generateFromIndex(IndexView index) {
        List<GeneratedSource> results = new ArrayList<>();

        Map<DotName, List<MethodInfo>> interfaceMethods = new LinkedHashMap<>();

        for (AnnotationInstance ann : index.getAnnotations(REQUIRES_CONFIRMATION)) {
            if (ann.target().kind() != AnnotationTarget.Kind.METHOD) continue;
            MethodInfo method = ann.target().asMethod();
            ClassInfo declaringClass = method.declaringClass();
            if (!java.lang.reflect.Modifier.isInterface(declaringClass.flags())) continue;

            interfaceMethods
                .computeIfAbsent(declaringClass.name(), k -> new ArrayList<>())
                .add(method);
        }

        for (var entry : interfaceMethods.entrySet()) {
            ClassInfo spiClass = index.getClassByName(entry.getKey());
            if (spiClass == null) continue;
            List<MethodInfo> annotatedMethods = entry.getValue();
            Set<String> annotatedMethodNames = new HashSet<>();
            for (MethodInfo m : annotatedMethods) {
                annotatedMethodNames.add(m.name());
            }

            String decoratorName = "Confirmed" + spiClass.simpleName();
            String fqcn = GENERATED_PACKAGE + "." + decoratorName;
            String source = generateDecoratorSource(spiClass, decoratorName,
                    annotatedMethods, annotatedMethodNames);
            results.add(new GeneratedSource(fqcn, source));
        }

        return results;
    }

    private String generateDecoratorSource(ClassInfo spiClass, String decoratorName,
                                           List<MethodInfo> annotatedMethods,
                                           Set<String> annotatedMethodNames) {
        StringBuilder sb = new StringBuilder();
        String spiSimpleName = spiClass.simpleName();
        String spiFullName = spiClass.name().toString();

        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");

        sb.append("import jakarta.decorator.Decorator;\n");
        sb.append("import jakarta.decorator.Delegate;\n");
        sb.append("import jakarta.annotation.Priority;\n");
        sb.append("import jakarta.inject.Inject;\n");
        sb.append("import jakarta.enterprise.inject.Any;\n");
        sb.append("import io.casehub.platform.confirmation.ConfirmationInterceptorCore;\n");
        sb.append("import io.casehub.platform.api.confirmation.OperationDescriptor;\n");
        sb.append("import io.casehub.platform.api.identity.CurrentPrincipal;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import ").append(spiFullName).append(";\n");

        Set<String> paramImports = collectParameterImports(spiClass);
        for (String imp : paramImports) {
            sb.append("import ").append(imp).append(";\n");
        }

        sb.append("\n");
        sb.append("// GENERATED by ConfirmationDecoratorProcessor — do not edit\n");
        sb.append("@Decorator\n");
        sb.append("@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 50)\n");
        sb.append("public class ").append(decoratorName);
        sb.append(" implements ").append(spiSimpleName).append(" {\n\n");

        sb.append("    @Inject @Delegate @Any ").append(spiSimpleName).append(" delegate;\n");
        sb.append("    @Inject ConfirmationInterceptorCore confirmationCore;\n");
        sb.append("    @Inject CurrentPrincipal currentPrincipal;\n\n");

        for (MethodInfo method : spiClass.methods()) {
            if (method.isSynthetic()) continue;
            if (annotatedMethodNames.contains(method.name())) {
                AnnotationInstance ann = method.annotation(REQUIRES_CONFIRMATION);
                String summaryTemplate = ann != null ? ann.value("summary").asString() : "";
                generateConfirmedMethod(sb, method, summaryTemplate);
            } else {
                generatePassThroughMethod(sb, method);
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void generateConfirmedMethod(StringBuilder sb, MethodInfo method,
                                          String summaryTemplate) {
        String returnType = typeToJava(method.returnType());
        boolean isVoid = method.returnType().kind() == Type.Kind.VOID;

        StringBuilder params = new StringBuilder();
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) {
                params.append(", ");
                args.append(", ");
            }
            String paramType = typeToJava(method.parameterTypes().get(i));
            String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;
            params.append(paramType).append(" ").append(paramName);
            args.append(paramName);
        }

        List<String> refs = SummaryTemplateParser.extractReferences(summaryTemplate);
        String summaryExpr = refs.isEmpty()
                ? "\"" + escapeJava(summaryTemplate) + "\""
                : SummaryTemplateParser.toJavaStringExpression(summaryTemplate, refs);

        sb.append("    @Override\n");
        sb.append("    public ").append(returnType).append(" ").append(method.name());
        sb.append("(").append(params).append(") {\n");

        sb.append("        OperationDescriptor descriptor = new OperationDescriptor(\n");
        sb.append("            ").append(summaryExpr).append(",\n");
        sb.append("            Map.of(");
        for (int i = 0; i < refs.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(SummaryTemplateParser.toMetadataEntry(refs.get(i)));
        }
        sb.append("));\n");

        sb.append("        confirmationCore.requireConfirmation(\n");
        sb.append("            currentPrincipal.actorId(), currentPrincipal.tenancyId(), descriptor);\n\n");

        if (isVoid) {
            sb.append("        delegate.").append(method.name()).append("(").append(args).append(");\n");
        } else {
            sb.append("        return delegate.").append(method.name()).append("(").append(args).append(");\n");
        }

        sb.append("    }\n\n");
    }

    private void generatePassThroughMethod(StringBuilder sb, MethodInfo method) {
        String returnType = typeToJava(method.returnType());
        boolean isVoid = method.returnType().kind() == Type.Kind.VOID;

        StringBuilder params = new StringBuilder();
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            if (i > 0) {
                params.append(", ");
                args.append(", ");
            }
            String paramType = typeToJava(method.parameterTypes().get(i));
            String paramName = method.parameterName(i) != null ? method.parameterName(i) : "arg" + i;
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

    private Set<String> collectParameterImports(ClassInfo spiClass) {
        Set<String> imports = new HashSet<>();
        for (MethodInfo method : spiClass.methods()) {
            if (method.isSynthetic()) continue;
            for (Type paramType : method.parameterTypes()) {
                addTypeImport(imports, paramType);
            }
            if (method.returnType().kind() != Type.Kind.VOID) {
                addTypeImport(imports, method.returnType());
            }
        }
        return imports;
    }

    private void addTypeImport(Set<String> imports, Type type) {
        switch (type.kind()) {
            case CLASS -> {
                String name = type.name().toString();
                if (!name.startsWith("java.lang.") || name.indexOf('.', 10) > 0) {
                    imports.add(name);
                }
            }
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
            if (processingEnv != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "Confirmation generator: failed to read Jandex indexes: " + e.getMessage());
            }
            return null;
        }

        if (indexes.isEmpty()) {
            return null;
        }

        if (processingEnv != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Confirmation generator: loaded " + indexes.size() + " Jandex index(es)");
        }
        return CompositeIndex.create(indexes);
    }

    private void writeSourceFile(GeneratedSource source) {
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(source.className());
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.print(source.sourceCode());
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Confirmation generator: generated " + source.className());

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Confirmation generator: failed to write " + source.className() + ": " + e.getMessage());
        }
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    record GeneratedSource(String className, String sourceCode) {}
}
