package io.casehub.yaml.core.orchestration;

import io.casehub.yaml.core.runtime.SpeedMultiplier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultScenarioScope implements ScenarioScope {

    private final    ConcurrentHashMap<String, Object> primitives      = new ConcurrentHashMap<>();
    private final    DefaultStepResultStore            resultStore     = new DefaultStepResultStore();
    private final    PrimitiveFactory                  factory;
    private final    SpeedMultiplier                   speedMultiplier;
    private final    DefaultScenarioScope              parent;
    private final    List<DefaultSpawnedTask>           spawnedTasks    = new CopyOnWriteArrayList<>();
    private final    List<DefaultScenarioScope>         children        = new CopyOnWriteArrayList<>();
    private volatile boolean                           closed          = false;
    private volatile boolean                           deadlineExpired = false;
    private volatile long                              deadlineRemainingNanos;
    private volatile Thread                            deadlineThread;

    private static final long JOIN_TIMEOUT_MS = 5000;

    public DefaultScenarioScope(PrimitiveFactory factory, SpeedMultiplier speedMultiplier) {
        this(factory, speedMultiplier, null);
    }

    DefaultScenarioScope(PrimitiveFactory factory, SpeedMultiplier speedMultiplier,
                         DefaultScenarioScope parent) {
        this.factory = factory;
        this.speedMultiplier = speedMultiplier;
        this.parent = parent;
    }

    public DefaultScenarioScope(PrimitiveFactory factory) {
        this(factory, SpeedMultiplier.identity());
    }

    public DefaultScenarioScope() {
        this(new DefaultPrimitiveFactory());
    }

    @Override
    public OrcSemaphore semaphore(String name, int permits) {
        return getOrCreate(name, OrcSemaphore.class, () -> factory.createSemaphore(name, permits));
    }

    @Override
    public OrcSemaphore semaphore(String name, int permits, java.time.Duration window) {
        return getOrCreate(name, OrcSemaphore.class, () -> factory.createSemaphore(name, permits, window));
    }

    @Override
    public OrcLatch latch(String name, int count) {
        return getOrCreate(name, OrcLatch.class, () -> factory.createLatch(name, count));
    }

    @Override
    public OrcSignal signal(String name) {
        return getOrCreate(name, OrcSignal.class, () -> factory.createSignal(name));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> OrcChannel<T> channel(String name) {
        return (OrcChannel<T>) getOrCreate(name, OrcChannel.class, () -> factory.createChannel(name));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> OrcChannel<T> channel(String name, int capacity) {
        return (OrcChannel<T>) getOrCreate(name, OrcChannel.class, () -> factory.createChannel(name, capacity));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends Enum<S>> BlockingOrcStateMachine<S> stateMachine(String name, Class<S> stateType, S initialState) {
        return (BlockingOrcStateMachine<S>) getOrCreate(name, BlockingOrcStateMachine.class, () -> factory.createStateMachine(name, stateType, initialState));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T primitive(String name, Class<T> type) {
        Object p = findPrimitive(name);
        if (p == null) {return null;}
        if (!type.isInstance(p)) {
            throw new IllegalArgumentException("Primitive '" + name + "' is " + p.getClass().getSimpleName() + ", not " + type.getSimpleName());
        }
        return (T) p;
    }

    @Override
    public StepResultStore resultStore() {
        return resultStore;
    }

    @Override
    public OrcCounter counter(String name) {
        return getOrCreate(name, OrcCounter.class, () -> factory.createCounter(name));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> OrcGauge<T> gauge(String name) {
        return (OrcGauge<T>) getOrCreate(name, OrcGauge.class, () -> factory.createGauge(name));
    }

    @Override
    public OrcFlag flag(String name) {
        return getOrCreate(name, OrcFlag.class, () -> factory.createFlag(name));
    }

    @Override
    public OrcAccumulator accumulator(String name, java.util.function.DoubleBinaryOperator op, double identity) {
        return getOrCreate(name, OrcAccumulator.class, () -> factory.createAccumulator(name, op, identity));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> OrcMap<K, V> map(String name) {
        return (OrcMap<K, V>) getOrCreate(name, OrcMap.class, () -> factory.createMap(name));
    }

    @Override
    public SpawnedTask spawn(String name, Runnable task) {
        if (closed) {throw new IllegalStateException("Cannot spawn on a closed scope");}
        var spawned = new DefaultSpawnedTask(name, task);
        spawnedTasks.add(spawned);
        spawned.start();
        return spawned;
    }

    @Override
    public ScenarioScope childScope(String name) {
        if (closed) {throw new IllegalStateException("Cannot create child scope on a closed scope");}
        var child = new DefaultScenarioScope(factory, speedMultiplier, this);
        children.add(child);
        return child;
    }

    @Override
    public void close() {
        if (closed) {return;}
        closed = true;

        if (deadlineThread != null) {deadlineThread.interrupt();}

        for (DefaultScenarioScope child : children) {
            child.close();
        }
        children.clear();

        for (DefaultSpawnedTask task : spawnedTasks) {
            task.interrupt();
        }
        for (DefaultSpawnedTask task : spawnedTasks) {
            try {
                task.joinInternal(JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        spawnedTasks.clear();

        for (Object p : primitives.values()) {
            if (p instanceof OrcPrimitive orc) {
                orc.releaseForClose();
            }
        }
        primitives.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrCreate(String name, Class<T> type, java.util.function.Supplier<T> factory) {
        Object existing = findPrimitive(name);
        if (existing != null) return (T) existing;
        return (T) primitives.computeIfAbsent(name, k -> factory.get());
    }

    Object findPrimitive(String name) {
        Object p = primitives.get(name);
        if (p != null) {return p;}
        return parent != null ? parent.findPrimitive(name) : null;
    }

    @Override
    public ScenarioScope withDeadline(Duration deadline) {
        return withDeadline(deadline, null);
    }

    @Override
    public ScenarioScope withDeadline(Duration deadline, Runnable onDeadline) {
        if (closed) {throw new IllegalStateException("Cannot set deadline on closed scope");}
        DefaultScenarioScope child = (DefaultScenarioScope) childScope("deadline");
        child.startDeadlineWatcher(deadline, onDeadline);
        return child;
    }

    @Override
    public boolean isDeadlineExpired() {
        if (deadlineExpired) {return true;}
        return parent != null && parent.isDeadlineExpired();
    }

    @Override
    public Optional<Duration> remainingTime() {
        if (deadlineExpired) {return Optional.of(Duration.ZERO);}
        long remaining = deadlineRemainingNanos;
        if (remaining > 0) {return Optional.of(Duration.ofNanos(remaining));}
        return parent != null ? parent.remainingTime() : Optional.empty();
    }

    private void startDeadlineWatcher(Duration scenarioDeadline, Runnable onDeadline) {
        SpeedMultiplier speed = this.speedMultiplier;
        deadlineRemainingNanos = scenarioDeadline.toNanos();

        deadlineThread = Thread.ofVirtual().name("deadline-watcher").start(() -> {
            double remainingScenario = scenarioDeadline.toNanos();

            while (remainingScenario > 0 && !closed) {
                double currentSpeed   = Math.max(speed.currentSpeed(), 0.001);
                long   realSleepNanos = (long) (remainingScenario / currentSpeed);
                long   maxSleep       = 1_000_000_000L;
                long   actualSleep    = Math.min(realSleepNanos, maxSleep);

                if (actualSleep <= 0) {break;}

                long beforeNanos = System.nanoTime();
                try {
                    Thread.sleep(Duration.ofNanos(actualSleep));
                } catch (InterruptedException e) {
                    return;
                }
                long elapsedReal = System.nanoTime() - beforeNanos;
                remainingScenario -= elapsedReal * currentSpeed;
                deadlineRemainingNanos = (long) remainingScenario;
            }

            if (!closed) {
                deadlineExpired = true;
                try {
                    if (onDeadline != null) {onDeadline.run();}
                } finally {
                    close();
                }
            }
        });
    }


    private static final class DefaultSpawnedTask implements SpawnedTask {
        private final    String    name;
        private final    Thread    thread;
        private volatile Throwable failure;
        private volatile boolean   done;

        DefaultSpawnedTask(String name, Runnable task) {
            this.name   = name;
            this.thread = Thread.ofVirtual().name("spawn-" + name).unstarted(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    failure = t;
                } finally {
                    done = true;
                }
            });
        }

        void start()                                               {thread.start();}

        void interrupt()                                           {thread.interrupt();}

        void joinInternal(long millis) throws InterruptedException {thread.join(millis);}

        @Override
        public String name()                                       {return name;}

        @Override
        public boolean isDone()                                    {return done;}

        @Override
        public boolean isFailed()                                  {return failure != null;}

        @Override
        public Throwable exception()                               {return failure;}

        @Override
        public void join() throws InterruptedException {thread.join();}

        @Override
        public boolean join(java.time.Duration timeout) throws InterruptedException {
            thread.join(timeout.toMillis());
            return done;
        }
    }
}
