package io.casehub.yaml.core.orchestration;

public interface OrcAccumulator {
    void accumulate(double value);
    double get();
    void reset();
}
