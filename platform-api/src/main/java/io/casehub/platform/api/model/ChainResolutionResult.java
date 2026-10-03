package io.casehub.platform.api.model;

import java.util.List;

public record ChainResolutionResult(
        ModelDescriptor resolvedModel,
        ModelChain.ModelChainEntry originalEntry,
        List<ModelChain.ModelChainEntry> attemptedEntries) {

    public boolean wasFallback() {
        return attemptedEntries.size() > 1;
    }

    public static ChainResolutionResult direct(ModelDescriptor model, ModelChain.ModelChainEntry entry) {
        return new ChainResolutionResult(model, entry, List.of(entry));
    }
}
