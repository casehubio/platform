package io.casehub.yaml.plugin.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.RecordComponentElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

class StreamBinderEmitter {

    void emit(PluginModel model, Filer filer) throws IOException {
        emitStreamingStep(model, filer);
        emitAction(model, filer);
    }

    private void emitStreamingStep(PluginModel model, Filer filer) throws IOException {
        String packageName = model.pluginClass().getEnclosingElement().toString();
        String simpleName = model.pluginClass().getSimpleName().toString();
        String className = simpleName + "StreamingStep";
        String fqcn = packageName + "." + className;

        String inputType = model.streamingInputType();
        String outputType = model.streamingOutputType();

        JavaFileObject file = filer.createSourceFile(fqcn, model.pluginClass());
        try (PrintWriter w = new PrintWriter(file.openWriter())) {
            w.println("package " + packageName + ";");
            w.println();
            w.println("import io.casehub.yaml.plugin.api.StreamingStep;");
            w.println("import io.casehub.yaml.plugin.api.ServiceRegistry;");
            w.println("import io.casehub.yaml.plugin.api.Result;");
            w.println("import java.util.Map;");
            w.println();
            w.println("public final class " + className + " implements StreamingStep<" + inputType + ", " + outputType + "> {");
            w.println();

            List<RecordComponentElement> fields = model.fields();
            List<PluginModel.ServiceParam> serviceParams = model.serviceParams();

            // Instance field: pre-constructed plugin record
            w.println("    private final " + simpleName + " instance;");
            for (int i = 0; i < serviceParams.size(); i++) {
                w.println("    private final " + serviceParams.get(i).qualifiedTypeName() + " service" + i + ";");
            }
            w.println();

            // Constructor
            w.print("    public " + className + "(");
            int argIdx = 0;
            for (RecordComponentElement field : fields) {
                if (argIdx > 0) w.print(", ");
                w.print(field.asType() + " " + field.getSimpleName());
                argIdx++;
            }
            for (int i = 0; i < serviceParams.size(); i++) {
                if (argIdx > 0) w.print(", ");
                w.print(serviceParams.get(i).qualifiedTypeName() + " service" + i);
                argIdx++;
            }
            w.println(") {");
            w.print("        this.instance = new " + simpleName + "(");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) w.print(", ");
                w.print(fields.get(i).getSimpleName());
            }
            w.println(");");
            for (int i = 0; i < serviceParams.size(); i++) {
                w.println("        this.service" + i + " = service" + i + ";");
            }
            w.println("    }");
            w.println();

            // inputType()
            w.println("    @Override");
            w.println("    public Class<" + inputType + "> inputType() { return " + inputType + ".class; }");
            w.println();

            // outputType()
            w.println("    @Override");
            w.println("    public Class<" + outputType + "> outputType() { return " + outputType + ".class; }");
            w.println();

            // apply()
            w.println("    @Override");
            w.println("    public " + outputType + " apply(" + inputType + " input) {");
            w.print("        return instance." + model.executeMethod().getSimpleName() + "(input");
            for (int i = 0; i < serviceParams.size(); i++) {
                w.print(", service" + i);
            }
            w.println(");");
            w.println("    }");
            w.println();

            // materialize()
            w.println("    @Override");
            w.println("    public Result materialize(Map<String, Object> params, ServiceRegistry services) {");
            w.println("        " + inputType + " input = (" + inputType + ") params.get(\"" + model.streamingInputKey() + "\");");
            w.println("        " + outputType + " output = apply(input);");
            w.println("        return Result.of(Map.of(OUTPUT_KEY, output));");
            w.println("    }");

            w.println("}");
        }
    }

    private void emitAction(PluginModel model, Filer filer) throws IOException {
        String packageName = model.pluginClass().getEnclosingElement().toString();
        String simpleName = model.pluginClass().getSimpleName().toString();
        String className = simpleName + "Action";
        String streamingStepClass = simpleName + "StreamingStep";
        String fqcn = packageName + "." + className;

        JavaFileObject file = filer.createSourceFile(fqcn, model.pluginClass());
        try (PrintWriter w = new PrintWriter(file.openWriter())) {
            w.println("package " + packageName + ";");
            w.println();
            w.println("import io.casehub.yaml.plugin.api.ServiceRegistry;");
            w.println("import io.casehub.yaml.plugin.api.Action;");
            w.println("import io.casehub.yaml.plugin.api.Result;");
            w.println("import java.util.Map;");
            w.println();
            w.println("public final class " + className + " implements Action {");
            w.println();
            w.println("    @Override");
            w.println("    public Result execute(Map<String, Object> parameters, ServiceRegistry services) {");

            // Construct streaming step with record fields and services
            w.print("        var step = new " + streamingStepClass + "(");
            List<RecordComponentElement> fields = model.fields();
            List<PluginModel.ServiceParam> serviceParams = model.serviceParams();
            int argIdx = 0;
            for (RecordComponentElement field : fields) {
                if (argIdx > 0) w.print(", ");
                String yamlKey = BinderEmitter.toKebabCase(field.getSimpleName().toString());
                w.print(BinderEmitter.paramExtraction(field.asType().toString(), yamlKey));
                argIdx++;
            }
            for (PluginModel.ServiceParam sp : serviceParams) {
                if (argIdx > 0) w.print(", ");
                w.print("services.lookup(" + sp.qualifiedTypeName() + ".class)");
                argIdx++;
            }
            w.println(");");
            w.println("        return step.materialize(parameters, services);");
            w.println("    }");
            w.println("}");
        }
    }
}
