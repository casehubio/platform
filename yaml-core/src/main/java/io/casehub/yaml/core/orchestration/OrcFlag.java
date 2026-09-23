package io.casehub.yaml.core.orchestration;

public interface OrcFlag extends OrcPrimitive {
    void set();
    void clear();
    boolean toggle();
    boolean get();
}
