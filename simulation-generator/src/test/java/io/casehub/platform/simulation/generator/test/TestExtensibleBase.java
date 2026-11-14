package io.casehub.platform.simulation.generator.test;

import java.util.Optional;

public interface TestExtensibleBase {
    default <T> Optional<T> as(Class<T> extensionType) {
        if (extensionType.isInstance(this)) {
            return Optional.of(extensionType.cast(this));
        }
        return Optional.empty();
    }
}
