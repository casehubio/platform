package io.casehub.platform.simulation.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.schema.generator.PlatformSchemaGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;

public final class LlmCorpusPopulator {

    private final Function<String, String> llmFunction;
    private final ObjectMapper objectMapper;
    private final PlatformSchemaGenerator schemaGen;

    public LlmCorpusPopulator(Function<String, String> llmFunction,
                               ObjectMapper objectMapper) {
        this.llmFunction = llmFunction;
        this.objectMapper = objectMapper;
        this.schemaGen = new PlatformSchemaGenerator();
    }

    public <I, O> void populate(CorpusSeed<I, O> seed,
                                 Class<I> inputType, Class<O> outputType,
                                 int count, String domainContext) {
        populate(seed, inputType, outputType, Function.identity(), count, domainContext);
    }

    @SuppressWarnings("unchecked")
    public <I, O, R> void populate(CorpusSeed<I, O> seed,
                                    Class<I> inputType, Class<R> rawOutputType,
                                    Function<R, O> outputAdapter,
                                    int count, String domainContext) {
        List<InvocationRecord<I, O>> existing = seed.build();
        JsonNode inputSchema = schemaGen.generate(inputType);
        JsonNode outputSchema = schemaGen.generate(rawOutputType);

        String prompt = buildPrompt(inputSchema, outputSchema, existing, count, domainContext);
        String response = llmFunction.apply(prompt);

        try {
            JsonNode parsed = objectMapper.readTree(response);
            for (JsonNode entry : parsed) {
                I input = objectMapper.treeToValue(entry.get("input"), inputType);
                R rawOutput = objectMapper.treeToValue(entry.get("output"), rawOutputType);
                seed.add(input, outputAdapter.apply(rawOutput));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse LLM corpus response", e);
        }
    }

    private <I, O> String buildPrompt(JsonNode inputSchema, JsonNode outputSchema,
                                       List<InvocationRecord<I, O>> existing,
                                       int count, String domainContext) {
        var sb = new StringBuilder();
        sb.append("Generate exactly ").append(count)
          .append(" corpus entries as a JSON array.\n\n");
        sb.append("Each entry must be a JSON object with \"input\" and \"output\" fields.\n\n");
        sb.append("Input JSON Schema:\n").append(inputSchema.toPrettyString()).append("\n\n");
        sb.append("Output JSON Schema:\n").append(outputSchema.toPrettyString()).append("\n\n");

        if (!existing.isEmpty()) {
            sb.append("Examples (generate entries consistent with these):\n");
            try {
                for (var record : existing) {
                    sb.append("{\"input\": ")
                      .append(objectMapper.writeValueAsString(record.input()))
                      .append(", \"output\": ")
                      .append(objectMapper.writeValueAsString(record.output()))
                      .append("}\n");
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to serialize examples", e);
            }
            sb.append("\n");
        }

        sb.append("Domain context: ").append(domainContext).append("\n\n");
        sb.append("Respond with ONLY the JSON array, no markdown, no explanation.");
        return sb.toString();
    }
}
