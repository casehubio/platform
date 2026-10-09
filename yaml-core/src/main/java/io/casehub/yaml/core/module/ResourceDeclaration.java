package io.casehub.yaml.core.module;

public record ResourceDeclaration(int concurrency) {
    public ResourceDeclaration {
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1, got: " + concurrency);
        }
    }
}
