package io.casehub.platform.agent;

public record ModelParameters(
        Double temperature,
        Integer maxTokens,
        Double topP,
        Integer topK
) {
    public static ModelParameters of() {
        return new ModelParameters(null, null, null, null);
    }

    public ModelParameters withTemperature(double temperature) {
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature must be between 0 and 2, got: " + temperature);
        }
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }

    public ModelParameters withMaxTokens(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive, got: " + maxTokens);
        }
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }

    public ModelParameters withTopP(double topP) {
        if (topP < 0 || topP > 1) {
            throw new IllegalArgumentException("topP must be between 0 and 1, got: " + topP);
        }
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }

    public ModelParameters withTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive, got: " + topK);
        }
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }
}
