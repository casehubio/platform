package io.casehub.yaml.core.orchestration;

import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.DoubleBinaryOperator;

public final class DefaultOrcAccumulator implements OrcAccumulator {

    private final DoubleAccumulator accumulator;
    private final java.util.concurrent.CopyOnWriteArrayList<java.util.function.DoubleConsumer> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();


    public DefaultOrcAccumulator(DoubleBinaryOperator op, double identity) {
        this.accumulator = new DoubleAccumulator(op, identity);
    }

    @Override
    public void accumulate(double value) {
        accumulator.accumulate(value);
        notifyListeners();
    }

    @Override
    public double get() { return accumulator.get(); }

    @Override
    public void reset() { accumulator.reset(); }

    @Override
    public double doubleValue() {
        return accumulator.get();
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
