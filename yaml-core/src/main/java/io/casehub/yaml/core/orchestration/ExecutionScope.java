package io.casehub.yaml.core.orchestration;

public interface ExecutionScope extends AutoCloseable {
    OrcSemaphore semaphore(String name, int permits);

    OrcSemaphore semaphore(String name, int permits, java.time.Duration window);

    OrcLatch latch(String name, int count);

    OrcSignal signal(String name);

    <T> OrcChannel<T> channel(String name);

    <T> OrcChannel<T> channel(String name, int capacity);

    <S> BlockingOrcStateMachine<S> stateMachine(String name, Class<S> stateType, S initialState);

    <T> T primitive(String name, Class<T> type);

    StepResultStore resultStore();

    OrcCounter counter(String name);

    <T> OrcGauge<T> gauge(String name);

    OrcFlag flag(String name);

    OrcAccumulator accumulator(String name, java.util.function.DoubleBinaryOperator op, double identity);

    <K, V> OrcMap<K, V> map(String name);


    SpawnedTask spawn(String name, Runnable task);

    ExecutionScope childScope(String name);

    ExecutionScope withDeadline(java.time.Duration deadline);

    ExecutionScope withDeadline(java.time.Duration deadline, Runnable onDeadline);

    boolean isDeadlineExpired();

    java.util.Optional<java.time.Duration> remainingTime();


    default io.casehub.yaml.core.runtime.SpeedMultiplier speedMultiplier() {
        return io.casehub.yaml.core.runtime.SpeedMultiplier.identity();
    }

    @Override
    void close();
}
