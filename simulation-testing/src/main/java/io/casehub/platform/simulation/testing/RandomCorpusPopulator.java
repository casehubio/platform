package io.casehub.platform.simulation.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.schema.generator.PlatformSchemaGenerator;
import io.casehub.schema.generator.SchemaDataGenerator;

import java.util.List;

public final class RandomCorpusPopulator {

    private RandomCorpusPopulator() {}

    public static <I, O> void populate(CorpusSeed<I, O> seed,
                                        Class<I> inputType,
                                        Class<O> outputType,
                                        int count,
                                        ObjectMapper mapper) {
        var schemaGen = new PlatformSchemaGenerator();
        var dataGen = new SchemaDataGenerator();
        JsonNode inputSchema = schemaGen.generate(inputType);
        JsonNode outputSchema = schemaGen.generate(outputType);
        List<I> inputs = dataGen.generate(inputSchema, count, inputType, mapper);
        List<O> outputs = dataGen.generate(outputSchema, count, outputType, mapper);
        for (int i = 0; i < count; i++) {
            seed.add(inputs.get(i), outputs.get(i));
        }
    }
}
