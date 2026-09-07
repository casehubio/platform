package io.casehub.schema.generator.module;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import java.util.Map;

public class ShorthandModule implements Module {

    private final Map<Class<?>, ShorthandDefinition> definitions;

    public ShorthandModule(Map<Class<?>, ShorthandDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
        builder.forTypesInGeneral()
            .withCustomDefinitionProvider((type, context) -> {
                ShorthandDefinition def = definitions.get(type.getErasedType());
                if (def == null) {
                    return null;
                }
                return buildOneOf(def, context.getGeneratorConfig());
            });
    }

    private CustomDefinition buildOneOf(ShorthandDefinition def, SchemaGeneratorConfig config) {
        ObjectNode schema = config.createObjectNode();
        ArrayNode oneOf = schema.putArray("oneOf");
        oneOf.add(def.scalarSchema(config));
        oneOf.add(def.objectSchema(config));
        return new CustomDefinition(schema);
    }
}
