package io.casehub.yaml.core.orchestration;

import java.util.concurrent.atomic.LongAdder;

public final class DefaultOrcCounter implements OrcCounter {

    private final LongAdder adder = new LongAdder();
    private final java.util.concurrent.CopyOnWriteArrayList<java.util.function.DoubleConsumer> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();


    @Override
    public void increment() {
        adder.increment();
        notifyListeners();
    }

    @Override
    public void decrement() {
        adder.decrement();
        notifyListeners();
    }

    @Override
    public void add(long delta) {
        adder.add(delta);
        notifyListeners();
    }

    @Override
    public long get() { return adder.sum(); }

    @Override
    public void reset() { adder.reset(); }

    @Override
    public double doubleValue() {
        return (double) adder.sum();
    }

    @Override
    public void onThresholdChange(java.util.function.DoubleConsumer listener) {
        listeners.add(listener);
    }

    @Override
    public void removeThresholdListener(java.util.function.DoubleConsumer listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        double val = doubleValue();
        for (var listener : listeners) {
            listener.accept(val);
        }
    }

}
