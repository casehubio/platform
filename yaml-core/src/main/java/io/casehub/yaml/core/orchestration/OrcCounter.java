package io.casehub.yaml.core.orchestration;

public interface OrcCounter extends OrcPrimitive {
    void increment();
    void decrement();
    void add(long delta);
    long get();
    void reset();
}
