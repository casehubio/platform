package io.casehub.schema.generator.module;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import java.util.function.Function;

public interface ShorthandDefinition {

    ObjectNode scalarSchema(SchemaGeneratorConfig config);

    ObjectNode objectSchema(SchemaGeneratorConfig config);

    static ShorthandDefinition of(
            Function<SchemaGeneratorConfig, ObjectNode> scalar,
            Function<SchemaGeneratorConfig, ObjectNode> object) {
        return new ShorthandDefinition() {
            @Override
            public ObjectNode scalarSchema(SchemaGeneratorConfig config) {
                return scalar.apply(config);
            }

            @Override
            public ObjectNode objectSchema(SchemaGeneratorConfig config) {
                return object.apply(config);
            }
        };
    }
}
