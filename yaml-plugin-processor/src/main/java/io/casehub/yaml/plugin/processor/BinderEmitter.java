package io.casehub.yaml.plugin.processor;

import io.casehub.yaml.plugin.api.Required;

import javax.annotation.processing.Filer;
import javax.lang.model.element.RecordComponentElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

class BinderEmitter {

    static String toKebabCase(String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    void emit(PluginModel model, Filer filer) throws IOException {
        String packageName = model.pluginClass().getEnclosingElement().toString();
        String simpleName = model.pluginClass().getSimpleName().toString();
        String className = simpleName + "Action";
        String fqcn = packageName + "." + className;

        JavaFileObject file = filer.createSourceFile(fqcn, model.pluginClass());
        try (PrintWriter w = new PrintWriter(file.openWriter())) {
            w.println("package " + packageName + ";");
            w.println();
            w.println("import io.casehub.yaml.plugin.api.ServiceRegistry;");
            w.println("import io.casehub.yaml.plugin.api.StepAction;");
            w.println("import io.casehub.yaml.plugin.api.StepResult;");
            w.println("import java.util.Map;");
            w.println();
            w.println("public final class " + className + " implements StepAction {");
            w.println();

            // name()
            w.println("    @Override");
            w.println("    public String name() {");
            w.println("        return \"" + model.name() + "\";");
            w.println("    }");
            w.println();

            // execute()
            w.println("    @Override");
            w.println("    public StepResult execute(Map<String, Object> parameters, ServiceRegistry services) {");

            // Validate required fields (kebab-case keys match YAML convention)
            for (RecordComponentElement field : model.fields()) {
                if (field.getAnnotation(Required.class) != null) {
                    String yamlKey = toKebabCase(field.getSimpleName().toString());
                    w.println("        if (!parameters.containsKey(\"" + yamlKey + "\")) {");
                    w.println("            throw new IllegalArgumentException(");
                    w.println("                \"" + model.name() + ": '" + yamlKey + "' is required\");");
                    w.println("        }");
                }
            }

            // Construct record (kebab-case keys → camelCase fields)
            w.print("        var spec = new " + simpleName + "(");
            List<RecordComponentElement> fields = model.fields();
            for (int i = 0; i < fields.size(); i++) {
                RecordComponentElement field = fields.get(i);
                String yamlKey = toKebabCase(field.getSimpleName().toString());
                String type = field.asType().toString();
                w.print(paramExtraction(type, yamlKey));
                if (i < fields.size() - 1) w.print(", ");
            }
            w.println(");");

            // Call @Execute method with service params
            w.print("        return spec." + model.executeMethod().getSimpleName() + "(");
            List<PluginModel.ServiceParam> serviceParams = model.serviceParams();
            for (int i = 0; i < serviceParams.size(); i++) {
                w.print("services.lookup(" + serviceParams.get(i).qualifiedTypeName() + ".class)");
                if (i < serviceParams.size() - 1) w.print(", ");
            }
            w.println(");");
            w.println("    }");
            w.println("}");
        }
    }

    private String paramExtraction(String type, String fieldName) {
        return switch (type) {
            case "int" -> "((Number) parameters.getOrDefault(\"" + fieldName + "\", 0)).intValue()";
            case "long" -> "((Number) parameters.getOrDefault(\"" + fieldName + "\", 0L)).longValue()";
            case "double" -> "((Number) parameters.getOrDefault(\"" + fieldName + "\", 0.0)).doubleValue()";
            case "boolean" -> "(Boolean) parameters.getOrDefault(\"" + fieldName + "\", false)";
            case "java.lang.String" -> "(String) parameters.get(\"" + fieldName + "\")";
            default -> "(" + type + ") parameters.get(\"" + fieldName + "\")";
        };
    }
}
