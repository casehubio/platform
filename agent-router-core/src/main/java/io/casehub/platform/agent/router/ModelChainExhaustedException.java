package io.casehub.platform.agent.router;

import io.casehub.platform.api.model.ModelChain;

import java.util.List;

public class ModelChainExhaustedException extends IllegalStateException {

    private final ModelChain chain;
    private final List<ModelChain.ModelChainEntry> attemptedEntries;

    public ModelChainExhaustedException(ModelChain chain, List<ModelChain.ModelChainEntry> attempted) {
        super("All %d chain entries exhausted".formatted(attempted.size()));
        this.chain = chain;
        this.attemptedEntries = List.copyOf(attempted);
    }

    public ModelChain chain() { return chain; }
    public List<ModelChain.ModelChainEntry> attemptedEntries() { return attemptedEntries; }
}
