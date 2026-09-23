package io.casehub.yaml.core.orchestration;

import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.DoubleBinaryOperator;

public final class DefaultOrcAccumulator implements OrcAccumulator {

    private final DoubleAccumulator accumulator;

    public DefaultOrcAccumulator(DoubleBinaryOperator op, double identity) {
        this.accumulator = new DoubleAccumulator(op, identity);
    }

    @Override
    public void accumulate(double value) { accumulator.accumulate(value); }

    @Override
    public double get() { return accumulator.get(); }

    @Override
    public void reset() { accumulator.reset(); }
}
