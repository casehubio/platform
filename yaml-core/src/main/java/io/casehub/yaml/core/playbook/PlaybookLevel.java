package io.casehub.yaml.core.playbook;

import java.util.Set;

public record PlaybookLevel(String name, int ordinal, Set<String> capabilities) {

    public PlaybookLevel {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PlaybookLevel requires a name");
        }
        if (ordinal < 1) {
            throw new IllegalArgumentException("PlaybookLevel ordinal must be >= 1");
        }
        capabilities = capabilities != null ? Set.copyOf(capabilities) : Set.of();
    }
}
