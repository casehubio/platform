package io.casehub.yaml.core.orchestration;

import io.casehub.yaml.core.runtime.SpeedMultiplier;

import java.time.Duration;
import java.util.function.DoubleBinaryOperator;

public final class DefaultPrimitiveFactory implements PrimitiveFactory {

    private final SpeedMultiplier speedMultiplier;

    public DefaultPrimitiveFactory(SpeedMultiplier speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    public DefaultPrimitiveFactory() {
        this(SpeedMultiplier.identity());
    }

    @Override
    public OrcSemaphore createSemaphore(String name, int permits) {
        return new DefaultOrcSemaphore(name, permits);
    }

    @Override
    public OrcSemaphore createSemaphore(String name, int permits, Duration window) {
        return new DefaultOrcSemaphore(name, permits, window);
    }

    @Override
    public OrcLatch createLatch(String name, int count) {
        return new DefaultOrcLatch(count);
    }

    @Override
    public OrcSignal createSignal(String name) {
        return new DefaultOrcSignal();
    }

    @Override
    public <T> OrcChannel<T> createChannel(String name) {
        return new DefaultOrcChannel<>(name);
    }

    @Override
    public <T> OrcChannel<T> createChannel(String name, int capacity) {
        return new DefaultOrcChannel<>(name, capacity);
    }

    @Override
    public <S extends Enum<S>> BlockingOrcStateMachine<S> createStateMachine(
            String name, Class<S> stateType, S initialState) {
        var delegate = DefaultOrcStateMachine.builder(name, stateType, initialState).build();
        return new DefaultBlockingOrcStateMachine<>(delegate, speedMultiplier);
    }

    @Override
    public OrcCounter createCounter(String name) {
        return new DefaultOrcCounter();
    }

    @Override
    public <T> OrcGauge<T> createGauge(String name) {
        return new DefaultOrcGauge<>();
    }

    @Override
    public OrcFlag createFlag(String name) {
        return new DefaultOrcFlag();
    }

    @Override
    public OrcAccumulator createAccumulator(String name, DoubleBinaryOperator op, double identity) {
        return new DefaultOrcAccumulator(op, identity);
    }

    @Override
    public <K, V> OrcMap<K, V> createMap(String name) {
        return new DefaultOrcMap<>();
    }
}
