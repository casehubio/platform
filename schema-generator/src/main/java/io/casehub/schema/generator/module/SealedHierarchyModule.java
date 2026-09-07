package io.casehub.schema.generator.module;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import java.util.Map;

public class SealedHierarchyModule implements Module {

    private final Map<Class<?>, Map<Class<?>, String>> discriminatorOverrides;

    public SealedHierarchyModule() {
        this(Map.of());
    }

    public SealedHierarchyModule(Map<Class<?>, Map<Class<?>, String>> overrides) {
        this.discriminatorOverrides = Map.copyOf(overrides);
    }

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
        builder.forTypesInGeneral()
            .withCustomDefinitionProvider((type, context) -> {
                Class<?> erasedType = type.getErasedType();
                if (!erasedType.isSealed()) {
                    return null;
                }
                ObjectNode schema = context.getGeneratorConfig().createObjectNode();
                ArrayNode oneOf = schema.putArray("oneOf");

                for (Class<?> subtype : erasedType.getPermittedSubclasses()) {
                    var resolvedSubtype = context.getTypeContext().resolve(subtype);
                    ObjectNode entry = context.createDefinitionReference(resolvedSubtype);
                    ObjectNode props = entry.putObject("properties");
                    String value = resolveDiscriminator(erasedType, subtype);
                    props.putObject("type").put("const", value);
                    entry.putArray("required").add("type");
                    oneOf.add(entry);
                }

                return new CustomDefinition(schema);
            });
    }

    private String resolveDiscriminator(Class<?> parent, Class<?> subtype) {
        return discriminatorOverrides
            .getOrDefault(parent, Map.of())
            .getOrDefault(subtype, defaultDiscriminator(subtype));
    }

    static String defaultDiscriminator(Class<?> subtype) {
        String name = subtype.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
