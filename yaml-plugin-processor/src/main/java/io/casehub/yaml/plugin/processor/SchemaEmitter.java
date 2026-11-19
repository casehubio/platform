package io.casehub.yaml.plugin.processor;

import io.casehub.yaml.plugin.api.Expression;
import io.casehub.yaml.plugin.api.Required;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
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
                RecordComponentElement field        = fields.get(i);
                String                 yamlKey      = BinderEmitter.toKebabCase(field.getSimpleName().toString());
                boolean                isExpression = field.getAnnotation(Expression.class) != null;
                if (isExpression) {
                    w.print("    \"" + yamlKey + "\": { \"type\": \"string\", \"format\": \"expression\" }");
                } else {
                    writeProperty(w, field.asType(), yamlKey, "    ");
                }
                if (i < fields.size() - 1) {w.print(",");}
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
            if (model.requiresConfirmation()) {
                w.println("  \"requiresConfirmation\": true,");
                w.println("  \"confirmationSummary\": \"" + escapeJson(model.confirmationSummary()) + "\",");
            }
            w.println("  \"additionalProperties\": false");
            w.println("}");
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toJsonType(String javaType) {
        return switch (javaType) {
            case "java.lang.String" -> "string";
            case "int", "long", "java.lang.Integer", "java.lang.Long" -> "integer";
            case "double", "float", "java.lang.Double", "java.lang.Float" -> "number";
            case "boolean", "java.lang.Boolean" -> "boolean";
            default -> {
                if (javaType.startsWith("java.util.List")) {yield "array";}
                if (javaType.startsWith("java.util.Map")) {yield "object";}
                yield "object";
            }
        };
    }

    private void writeProperty(PrintWriter w, TypeMirror type, String key, String indent) {
        if (type instanceof DeclaredType dt) {
            String typeName = ((TypeElement) dt.asElement()).getQualifiedName().toString();
            if (typeName.equals("java.util.List") && !dt.getTypeArguments().isEmpty()) {
                TypeMirror elementType = dt.getTypeArguments().get(0);
                if (isRecordType(elementType)) {
                    writeArrayRecordProperty(w, (DeclaredType) elementType, key, indent);
                } else {
                    String itemType = toJsonType(elementType.toString());
                    w.print(indent + "\"" + key + "\": { \"type\": \"array\", \"items\": { \"type\": \"" + itemType + "\" } }");
                }
                return;
            }
            if (isRecordType(type)) {
                writeRecordProperty(w, dt, key, indent);
                return;
            }
        }
        w.print(indent + "\"" + key + "\": { \"type\": \"" + toJsonType(type.toString()) + "\" }");
    }

    private void writeArrayRecordProperty(PrintWriter w, DeclaredType elementType, String key, String indent) {
        w.println(indent + "\"" + key + "\": {");
        w.println(indent + "  \"type\": \"array\",");
        w.print(indent + "  \"items\": ");
        writeRecordSchema(w, elementType, indent + "  ");
        w.println();
        w.print(indent + "}");
    }

    private void writeRecordProperty(PrintWriter w, DeclaredType type, String key, String indent) {
        w.print(indent + "\"" + key + "\": ");
        writeRecordSchema(w, type, indent);
    }

    private void writeRecordSchema(PrintWriter w, DeclaredType type, String indent) {
        TypeElement                            recordElement = (TypeElement) type.asElement();
        List<? extends RecordComponentElement> components    = recordElement.getRecordComponents();

        w.println("{");
        w.println(indent + "  \"type\": \"object\",");
        w.println(indent + "  \"properties\": {");

        for (int j = 0; j < components.size(); j++) {
            RecordComponentElement comp    = components.get(j);
            String                 compKey = BinderEmitter.toKebabCase(comp.getSimpleName().toString());
            writeProperty(w, comp.asType(), compKey, indent + "    ");
            if (j < components.size() - 1) {w.print(",");}
            w.println();
        }

        w.println(indent + "  },");
        List<String> required = components.stream()
                                          .map(c -> "\"" + BinderEmitter.toKebabCase(c.getSimpleName().toString()) + "\"")
                                          .toList();
        w.println(indent + "  \"required\": [" + String.join(", ", required) + "]");
        w.print(indent + "}");
    }


    private boolean isRecordType(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            return dt.asElement().getKind() == ElementKind.RECORD;
        }
        return false;
    }


}
