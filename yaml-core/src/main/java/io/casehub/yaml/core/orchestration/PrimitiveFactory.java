package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.function.DoubleBinaryOperator;

public interface PrimitiveFactory {

    OrcSemaphore createSemaphore(String name, int permits);

    OrcSemaphore createSemaphore(String name, int permits, Duration window);

    OrcLatch createLatch(String name, int count);

    OrcSignal createSignal(String name);

    <T> OrcChannel<T> createChannel(String name);

    <T> OrcChannel<T> createChannel(String name, int capacity);

    <S extends Enum<S>> BlockingOrcStateMachine<S> createStateMachine(
            String name, Class<S> stateType, S initialState);

    OrcCounter createCounter(String name);

    <T> OrcGauge<T> createGauge(String name);

    OrcFlag createFlag(String name);

    OrcAccumulator createAccumulator(String name, DoubleBinaryOperator op, double identity);

    <K, V> OrcMap<K, V> createMap(String name);
}
