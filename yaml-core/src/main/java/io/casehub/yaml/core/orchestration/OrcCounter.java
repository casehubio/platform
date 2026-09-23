package io.casehub.yaml.core.orchestration;

public interface OrcCounter {
    void increment();
    void decrement();
    void add(long delta);
    long get();
    void reset();
}
