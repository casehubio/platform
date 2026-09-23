package io.casehub.yaml.core.orchestration;

public interface OrcGauge<T> extends OrcPrimitive {
    void set(T value);
    T get();
    boolean compareAndSet(T expect, T update);
}
