package io.casehub.platform.agent.router;

import io.casehub.platform.api.model.ModelChain;

class ModelChainRetryableException extends RuntimeException {

    private final ModelChain.ModelChainEntry failedEntry;

    ModelChainRetryableException(ModelChain.ModelChainEntry entry, Throwable cause) {
        super("Chain entry %s failed: %s".formatted(entry, cause.getMessage()), cause);
        this.failedEntry = entry;
    }

    ModelChain.ModelChainEntry failedEntry() { return failedEntry; }
}
