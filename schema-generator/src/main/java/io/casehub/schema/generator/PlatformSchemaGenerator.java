package io.casehub.schema.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import io.casehub.schema.generator.module.EnumInliningModule;
import io.casehub.schema.generator.module.UnevaluatedPropertiesModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlatformSchemaGenerator {

    private final SchemaGenerator generator;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlatformSchemaGenerator(Module... customModules) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(Option.DEFINITIONS_FOR_ALL_OBJECTS)
                .with(Option.FLATTENED_ENUMS_FROM_TOSTRING)
                .with(new JacksonModule())
                .with(new JakartaValidationModule())
                .with(new EnumInliningModule())
                .with(new UnevaluatedPropertiesModule());
        for (Module m : customModules) {
            configBuilder.with(m);
        }
        this.generator = new SchemaGenerator(configBuilder.build());
    }

    public JsonNode generate(Class<?> rootType) {
        JsonNode schema = generator.generateSchema(rootType);
        return SchemaPostProcessor.process(schema);
    }

    public void generateToJson(Class<?> rootType, Path output) throws IOException {
        JsonNode schema = generate(rootType);
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(schema));
    }
}
