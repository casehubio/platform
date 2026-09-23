package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultScenarioScope implements ScenarioScope {

    private final ConcurrentHashMap<String, Object> primitives = new ConcurrentHashMap<>();
    private final DefaultStepResultStore resultStore = new DefaultStepResultStore();
    private volatile boolean closed = false;

    @Override
    public OrcSemaphore semaphore(String name, int permits) {
        return getOrCreate(name, OrcSemaphore.class,
                () -> new DefaultOrcSemaphore(name, permits));
    }

    @Override
    public OrcSemaphore semaphore(String name, int permits, Duration window) {
        return getOrCreate(name, OrcSemaphore.class,
                () -> new DefaultOrcSemaphore(name, permits, window));
    }

    @Override
    public OrcLatch latch(String name, int count) {
        return getOrCreate(name, OrcLatch.class,
                () -> new DefaultOrcLatch(count));
    }

    @Override
    public OrcSignal signal(String name) {
        return getOrCreate(name, OrcSignal.class,
                () -> new DefaultOrcSignal());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> OrcChannel<T> channel(String name) {
        return (OrcChannel<T>) getOrCreate(name, OrcChannel.class,
                () -> new DefaultOrcChannel<>(name));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> OrcChannel<T> channel(String name, int capacity) {
        return (OrcChannel<T>) getOrCreate(name, OrcChannel.class,
                () -> new DefaultOrcChannel<>(name, capacity));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends Enum<S>> OrcStateMachine<S> stateMachine(String name, Class<S> stateType, S initialState) {
        return (OrcStateMachine<S>) getOrCreate(name, OrcStateMachine.class,
                () -> DefaultOrcStateMachine.builder(name, stateType, initialState).build());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T primitive(String name, Class<T> type) {
        Object p = primitives.get(name);
        if (p == null) return null;
        if (!type.isInstance(p)) {
            throw new IllegalArgumentException("Primitive '" + name + "' is " + p.getClass().getSimpleName()
                    + ", not " + type.getSimpleName());
        }
        return (T) p;
    }

    @Override
    public StepResultStore resultStore() {
        return resultStore;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        for (Object p : primitives.values()) {
            if (p instanceof DefaultOrcChannel<?> ch) {
                ch.close();
            } else if (p instanceof DefaultOrcLatch latch) {
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
            } else if (p instanceof DefaultOrcSignal signal) {
                if (!signal.isSignalled()) {
                    signal.signal();
                }
            } else if (p instanceof DefaultOrcSemaphore sem) {
                sem.shutdown();
            }
        }
        primitives.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrCreate(String name, Class<T> type, java.util.function.Supplier<T> factory) {
        return (T) primitives.computeIfAbsent(name, k -> factory.get());
    }
}
