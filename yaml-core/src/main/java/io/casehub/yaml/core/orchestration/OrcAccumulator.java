package io.casehub.yaml.core.orchestration;

public interface OrcAccumulator extends OrcPrimitive {
    void accumulate(double value);
    double get();
    void reset();
}
