package io.casehub.platform.simulation.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.simulation.SimilarityScorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecordFieldScorer<I> implements SimilarityScorer<I> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final List<FieldSpec> fields;
    private final ObjectMapper mapper;

    private RecordFieldScorer(final List<FieldSpec> fields, final ObjectMapper mapper) {
        this.fields = List.copyOf(fields);
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public double score(final I query, final I candidate) {
        final Map<String, Object> queryMap = toMap(query);
        final Map<String, Object> candidateMap = toMap(candidate);

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (final FieldSpec field : fields) {
            final Object qVal = queryMap.get(field.name());
            final Object cVal = candidateMap.get(field.name());
            weightedSum += field.weight() * field.scorer().score(qVal, cVal);
            totalWeight += field.weight();
        }
        return totalWeight > 0.0 ? weightedSum / totalWeight : 0.0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(final Object input) {
        if (input instanceof Map) {
            return (Map<String, Object>) input;
        }
        return mapper.convertValue(input, MAP_TYPE);
    }

    public static <I> Builder<I> builder() {
        return new Builder<>();
    }

    public static final class Builder<I> {
        private final List<FieldSpec> fields = new ArrayList<>();

        public Builder<I> field(final String name, final FieldSimilarity scorer,
                                final double weight) {
            fields.add(new FieldSpec(name, scorer, weight));
            return this;
        }

        public RecordFieldScorer<I> build() {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return new RecordFieldScorer<>(fields, mapper);
        }
    }

    record FieldSpec(String name, FieldSimilarity scorer, double weight) {}
}
