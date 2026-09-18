package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.SimilarityScorer;
import io.casehub.platform.simulation.SimulationConfigException;

public class DeclarativeScorerFactory {

    public SimilarityScorer<Object> create(final String spec) {
        if (!spec.startsWith("fields:")) {
            throw new SimulationConfigException(
                    "Unknown scorer spec: " + spec + ". Valid: fields:<name>:<scorer>:<weight>,...");
        }

        final String fieldsDef = spec.substring("fields:".length());
        final String[] fieldSpecs = fieldsDef.split(",");
        final RecordFieldScorer.Builder<Object> builder = RecordFieldScorer.builder();

        for (final String fieldSpec : fieldSpecs) {
            final String[] parts = fieldSpec.split(":");
            if (parts.length != 3) {
                throw new SimulationConfigException(
                        "Invalid field spec: " + fieldSpec + ". Expected: <name>:<scorer>:<weight>");
            }
            final String name = parts[0];
            final FieldSimilarity scorer = resolveScorer(parts[1]);
            final double weight = Double.parseDouble(parts[2]);
            builder.field(name, scorer, weight);
        }

        return builder.build();
    }

    private FieldSimilarity resolveScorer(final String name) {
        return switch (name) {
            case "exact" -> FieldSimilarity.EXACT;
            case "substring" -> FieldSimilarity.SUBSTRING;
            case "numeric-range" -> FieldSimilarity.NUMERIC_RANGE;
            case "ignore" -> FieldSimilarity.IGNORE;
            default -> throw new SimulationConfigException(
                    "Unknown field scorer: " + name + ". Valid: exact, substring, numeric-range, ignore");
        };
    }
}
