package io.casehub.yaml.core.orchestration;

import java.time.Duration;

public interface ScenarioScope extends AutoCloseable {
    OrcSemaphore semaphore(String name, int permits);
    OrcSemaphore semaphore(String name, int permits, Duration window);
    OrcLatch latch(String name, int count);
    OrcSignal signal(String name);
    <T> OrcChannel<T> channel(String name);
    <T> OrcChannel<T> channel(String name, int capacity);
    <S extends Enum<S>> OrcStateMachine<S> stateMachine(String name, Class<S> stateType, S initialState);
    <T> T primitive(String name, Class<T> type);
    StepResultStore resultStore();
    @Override void close();
}
