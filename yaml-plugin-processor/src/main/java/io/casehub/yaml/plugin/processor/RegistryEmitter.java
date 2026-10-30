package io.casehub.yaml.plugin.processor;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;

class RegistryEmitter {

    void emit(PluginModel model, Filer filer) throws IOException {
        FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
            "META-INF/yaml-plugins/" + model.name() + ".json");

        String actionFqcn = model.pluginClass().getEnclosingElement().toString()
            + "." + model.pluginClass().getSimpleName() + "Action";

        try (PrintWriter w = new PrintWriter(file.openWriter())) {
            w.println("{");
            w.println("  \"name\": \"" + model.name() + "\",");
            w.println("  \"description\": \"" + model.description() + "\",");
            w.println("  \"capability\": \"" + model.capability() + "\",");
            w.println("  \"pluginClass\": \"" + model.pluginClass().getQualifiedName() + "\",");
            w.println("  \"actionClass\": \"" + actionFqcn + "\",");
            w.println("  \"portability\": \"" + model.portability() + "\",");
            w.print("  \"schemaResource\": \"META-INF/yaml-plugins/" + model.name() + ".schema.json\"");
            if (model.streaming()) {
                w.println(",");
                w.println("  \"streaming\": {");
                w.println("    \"inputType\": \"" + model.streamingInputType() + "\",");
                w.println("    \"outputType\": \"" + model.streamingOutputType() + "\",");
                w.println("    \"inputKey\": \"" + model.streamingInputKey() + "\",");
                String streamingStepFqcn = model.pluginClass().getEnclosingElement().toString()
                    + "." + model.pluginClass().getSimpleName() + "StreamingStep";
                w.println("    \"streamingStepClass\": \"" + streamingStepFqcn + "\"");
                w.println("  }");
            } else {
                w.println();
            }
            w.println("}");
        }
    }
}
