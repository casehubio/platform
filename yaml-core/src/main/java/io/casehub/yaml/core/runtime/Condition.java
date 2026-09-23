package io.casehub.yaml.core.runtime;

@FunctionalInterface
public interface Condition {
    boolean evaluate();
}
