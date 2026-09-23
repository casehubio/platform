package io.casehub.yaml.core.orchestration;

import java.util.concurrent.atomic.LongAdder;

public final class DefaultOrcCounter implements OrcCounter {

    private final LongAdder adder = new LongAdder();

    @Override
    public void increment() { adder.increment(); }

    @Override
    public void decrement() { adder.decrement(); }

    @Override
    public void add(long delta) { adder.add(delta); }

    @Override
    public long get() { return adder.sum(); }

    @Override
    public void reset() { adder.reset(); }
}
