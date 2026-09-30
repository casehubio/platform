package io.casehub.yaml.step.resolver;

import io.casehub.yaml.core.resolver.VariableSource;

import java.util.Objects;
import java.util.function.Function;

public class ConfigVariableSource implements VariableSource {

    private final Function<String, String> lookup;

    public ConfigVariableSource(Function<String, String> lookup) {
        this.lookup = Objects.requireNonNull(lookup);
    }

    @Override
    public String resolve(String name) {
        return lookup.apply(name);
    }
}
