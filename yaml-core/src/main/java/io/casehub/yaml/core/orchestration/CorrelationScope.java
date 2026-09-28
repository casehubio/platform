package io.casehub.yaml.core.orchestration;

import io.casehub.yaml.core.runtime.SpeedMultiplier;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class CorrelationScope<K, V> implements OrcPrimitive, AutoCloseable {

    private static final long BUFFER_GRACE_NANOS = Duration.ofSeconds(1).toNanos();

    private final OrcChannel<V> channel;
    private final Function<V, K> keyExtractor;
    private final SpeedMultiplier speedMultiplier;
    private final ConcurrentHashMap<K, PendingCorrelation<K, V>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, BufferedValue<V>> earlyArrivals = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Thread listenerThread;
    private volatile boolean closed;

    record PendingCorrelation<K, V>(K key, CompletableFuture<V> future,
                                    long registeredAtNanos, ScheduledFuture<?> timeoutTask) {}

    record BufferedValue<V>(V value, long arrivedAtNanos) {}

    public CorrelationScope(OrcChannel<V> channel, Function<V, K> keyExtractor) {
        this(channel, keyExtractor, SpeedMultiplier.identity());
    }

    public CorrelationScope(OrcChannel<V> channel, Function<V, K> keyExtractor,
                            SpeedMultiplier speedMultiplier) {
        this.channel = channel;
        this.keyExtractor = keyExtractor;
        this.speedMultiplier = speedMultiplier;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r ->
            Thread.ofVirtual().name("correlation-timeout").unstarted(r));
        this.scheduler.scheduleAtFixedRate(this::evictStaleBuffer,
            BUFFER_GRACE_NANOS, BUFFER_GRACE_NANOS, TimeUnit.NANOSECONDS);
        this.listenerThread = Thread.ofVirtual().name("correlation-listener").start(this::listenerLoop);
    }

    public static <K, V> CorrelationScope<K, V> forScope(
            ScenarioScope scope, String name, Function<V, K> keyExtractor) {
        if (!(scope instanceof DefaultScenarioScope dss)) {
            throw new IllegalArgumentException(
                "forScope() requires DefaultScenarioScope. Use the OrcChannel constructor for custom implementations.");
        }
        OrcChannel<V> channel = scope.channel(name + ".correlation");
        CorrelationScope<K, V> cs = new CorrelationScope<>(channel, keyExtractor, scope.speedMultiplier());
        dss.registerPrimitive(name, cs);
        return cs;
    }

    public void expectResponse(K correlationKey, Duration timeout) {
        if (closed) throw new IllegalStateException("CorrelationScope is closed");

        double speed = Math.max(speedMultiplier.currentSpeed(), 0.001);
        long realTimeoutNanos = (long) (timeout.toNanos() / speed);
        CompletableFuture<V> future = new CompletableFuture<>();
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            PendingCorrelation<K, V> removed = pending.remove(correlationKey);
            if (removed != null) {
                removed.future().completeExceptionally(
                    new CorrelationTimeoutException(correlationKey, timeout));
            }
        }, realTimeoutNanos, TimeUnit.NANOSECONDS);

        PendingCorrelation<K, V> pc = new PendingCorrelation<>(
            correlationKey, future, System.nanoTime(), timeoutTask);

        PendingCorrelation<K, V> existing = pending.putIfAbsent(correlationKey, pc);
        if (existing != null) {
            timeoutTask.cancel(false);
            throw new IllegalStateException("Correlation key already pending: " + correlationKey);
        }

        if (closed) {
            PendingCorrelation<K, V> removed = pending.remove(correlationKey);
            if (removed != null) {
                timeoutTask.cancel(false);
                future.cancel(true);
            }
            throw new IllegalStateException("CorrelationScope closed during registration");
        }

        BufferedValue<V> buffered = earlyArrivals.remove(correlationKey);
        if (buffered != null) {
            timeoutTask.cancel(false);
            future.complete(buffered.value());
        }
    }

    public V awaitResponse(K correlationKey) throws InterruptedException, TimeoutException {
        PendingCorrelation<K, V> pc = pending.get(correlationKey);
        if (pc == null) {
            throw new IllegalStateException("No pending correlation for key: " + correlationKey);
        }
        try {
            return pc.future().get();
        } catch (CancellationException e) {
            throw new InterruptedException("correlation scope closed");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CorrelationTimeoutException te) throw te;
            if (cause instanceof InterruptedException ie) throw ie;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } finally {
            pending.remove(correlationKey);
        }
    }

    public boolean hasPending(K correlationKey) {
        PendingCorrelation<K, V> pc = pending.get(correlationKey);
        return pc != null && !pc.future().isDone();
    }

    public int pendingCount() { return pending.size(); }

    public Optional<Duration> oldestPendingAge() {
        long now = System.nanoTime();
        long oldest = Long.MAX_VALUE;
        for (PendingCorrelation<K, V> pc : pending.values()) {
            if (!pc.future().isDone()) {
                oldest = Math.min(oldest, pc.registeredAtNanos());
            }
        }
        if (oldest == Long.MAX_VALUE) return Optional.empty();
        return Optional.of(Duration.ofNanos(now - oldest));
    }

    @Override
    public void releaseForClose() { close(); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        for (PendingCorrelation<K, V> pc : pending.values()) {
            pc.future().cancel(true);
            if (pc.timeoutTask() != null) pc.timeoutTask().cancel(false);
        }
        pending.clear();
        earlyArrivals.clear();

        scheduler.shutdownNow();
        if (listenerThread != null) listenerThread.interrupt();
    }

    private void listenerLoop() {
        while (!closed) {
            V value;
            try {
                value = channel.receive();
            } catch (ChannelClosedException e) {
                failAllPending(e.getCause() != null ? e.getCause() : e);
                return;
            } catch (InterruptedException e) {
                return;
            }
            if (value == null) return;

            K key;
            try {
                key = keyExtractor.apply(value);
            } catch (Exception e) {
                continue;
            }
            if (key == null) continue;

            PendingCorrelation<K, V> pc = pending.get(key);
            if (pc != null) {
                if (pc.timeoutTask() != null) pc.timeoutTask().cancel(false);
                pc.future().complete(value);
            } else {
                earlyArrivals.put(key, new BufferedValue<>(value, System.nanoTime()));
            }
        }
    }

    private void failAllPending(Throwable cause) {
        for (PendingCorrelation<K, V> pc : pending.values()) {
            pc.future().completeExceptionally(cause);
            if (pc.timeoutTask() != null) pc.timeoutTask().cancel(false);
        }
        pending.clear();
    }

    private void evictStaleBuffer() {
        long now = System.nanoTime();
        earlyArrivals.entrySet().removeIf(e ->
            (now - e.getValue().arrivedAtNanos()) > BUFFER_GRACE_NANOS);
    }
}
