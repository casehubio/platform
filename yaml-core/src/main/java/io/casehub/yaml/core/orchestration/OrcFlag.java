package io.casehub.yaml.core.orchestration;

public interface OrcFlag {
    void set();
    void clear();
    boolean toggle();
    boolean get();
}
