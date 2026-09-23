package io.casehub.yaml.core.orchestration;

import java.util.concurrent.atomic.AtomicReference;

public final class DefaultOrcGauge<T> implements OrcGauge<T> {

    private final AtomicReference<T> ref = new AtomicReference<>();

    @Override
    public void set(T value) { ref.set(value); }

    @Override
    public T get() { return ref.get(); }

    @Override
    public boolean compareAndSet(T expect, T update) { return ref.compareAndSet(expect, update); }
}
