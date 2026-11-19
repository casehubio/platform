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
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }

    public ModelParameters withMaxTokens(int maxTokens) {
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }

    public ModelParameters withTopP(double topP) {
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }

    public ModelParameters withTopK(int topK) {
        return new ModelParameters(temperature, maxTokens, topP, topK);
    }
}
