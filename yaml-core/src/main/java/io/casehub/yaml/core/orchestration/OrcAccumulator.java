package io.casehub.yaml.core.orchestration;

public interface OrcAccumulator extends OrcNumericPrimitive {
    void accumulate(double value);
    double get();
    void reset();
}
