package io.casehub.schema.generator.module;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;

public class UnevaluatedPropertiesModule implements Module {

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
        builder.forTypesInGeneral()
                .withTypeAttributeOverride((node, scope, context) -> {
                    if (node instanceof ObjectNode obj && obj.has("properties")) {
                        obj.put("unevaluatedProperties", false);
                    }
                });
    }
}
