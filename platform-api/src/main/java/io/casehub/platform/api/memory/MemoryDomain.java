package io.casehub.platform.api.memory;

import java.util.Objects;

public record MemoryDomain(String name) {
    public MemoryDomain {
        Objects.requireNonNull(name, "domain name must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("domain name must not be blank");
    }
}
