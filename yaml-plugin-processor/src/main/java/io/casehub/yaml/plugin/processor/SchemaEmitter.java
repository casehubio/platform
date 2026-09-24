package io.casehub.yaml.plugin.processor;

import io.casehub.yaml.plugin.api.Required;

import javax.annotation.processing.Filer;
import javax.lang.model.element.RecordComponentElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

class SchemaEmitter {

    void emit(PluginModel model, Filer filer) throws IOException {
        FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
            "META-INF/yaml-plugins/" + model.name() + ".schema.json");

        try (PrintWriter w = new PrintWriter(file.openWriter())) {
            w.println("{");
            w.println("  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",");
            w.println("  \"type\": \"object\",");
            w.println("  \"properties\": {");

            List<RecordComponentElement> fields = model.fields();
            for (int i = 0; i < fields.size(); i++) {
                RecordComponentElement field = fields.get(i);
                String yamlKey = BinderEmitter.toKebabCase(field.getSimpleName().toString());
                String jsonType = toJsonType(field.asType().toString());
                w.print("    \"" + yamlKey + "\": { \"type\": \"" + jsonType + "\" }");
                if (i < fields.size() - 1) w.print(",");
                w.println();
            }

            w.println("  },");
            w.print("  \"required\": [");
            List<String> requiredFields = fields.stream()
                .filter(f -> f.getAnnotation(Required.class) != null)
                .map(f -> "\"" + BinderEmitter.toKebabCase(f.getSimpleName().toString()) + "\"")
                .toList();
            w.print(String.join(", ", requiredFields));
            w.println("],");
            w.println("  \"additionalProperties\": false");
            w.println("}");
        }
    }

    private String toJsonType(String javaType) {
        return switch (javaType) {
            case "java.lang.String" -> "string";
            case "int", "long", "java.lang.Integer", "java.lang.Long" -> "integer";
            case "double", "float", "java.lang.Double", "java.lang.Float" -> "number";
            case "boolean", "java.lang.Boolean" -> "boolean";
            default -> {
                if (javaType.startsWith("java.util.List")) yield "array";
                if (javaType.startsWith("java.util.Map")) yield "object";
                yield "object";
            }
        };
    }
}
