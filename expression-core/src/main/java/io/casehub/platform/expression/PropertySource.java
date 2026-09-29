package io.casehub.platform.expression;

import java.util.Optional;

public interface PropertySource {
    Optional<String> getProperty(String name);
    Iterable<String> getPropertyNames();
}
