package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.runtime.SpeedMultiplier;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

public final class InlineDecoratorWrappers {

    private InlineDecoratorWrappers() {}

    static long computeDelay(long baseMs, String backoff, int attempt) {
        return switch (backoff) {
            case "exponential" -> baseMs * (1L << attempt);
            case "exponential-with-jitter" -> {
                long exp = baseMs * (1L << attempt);
                yield exp + (long) (Math.random() * exp);
            }
            default -> baseMs;
        };
    }

    public static <I, O> Function<I, O> withTimeout(Function<I, O> inner, Duration timeout,
                                                      SpeedMultiplier speedMultiplier) {
        return input -> {
            long effectiveMs = (long) (timeout.toMillis() / speedMultiplier.currentSpeed());
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            Future<O> future = executor.submit(() -> inner.apply(input));
            try {
                return future.get(effectiveMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Timeout after " + timeout, e);
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw new RuntimeException(e.getCause());
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            } finally {
                executor.shutdownNow();
            }
        };
    }

    public static <I, O> Function<I, O> withTimeout(Function<I, O> inner, Duration timeout) {
        return withTimeout(inner, timeout, SpeedMultiplier.identity());
    }

    public static <I, O> Function<I, O> withRetry(Function<I, O> inner,
            int maxRetries, Duration delay, String backoff, SpeedMultiplier speedMultiplier) {
        return input -> {
            RuntimeException last = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return inner.apply(input);
                } catch (RuntimeException e) {
                    last = e;
                    if (attempt < maxRetries) {
                        long delayMs = computeDelay(delay.toMillis(), backoff, attempt);
                        long effectiveMs = (long) (delayMs / speedMultiplier.currentSpeed());
                        LockSupport.parkNanos(effectiveMs * 1_000_000);
                    }
                }
            }
            throw last;
        };
    }

    public static <I, O> Function<I, O> withRetry(Function<I, O> inner,
            int maxRetries, Duration delay, String backoff) {
        return withRetry(inner, maxRetries, delay, backoff, SpeedMultiplier.identity());
    }

    public static <I, O> Function<I, O> withDelay(Function<I, O> inner, Duration delay,
                                                    SpeedMultiplier speedMultiplier) {
        return input -> {
            long effectiveNanos = (long) (delay.toNanos() / speedMultiplier.currentSpeed());
            LockSupport.parkNanos(effectiveNanos);
            return inner.apply(input);
        };
    }

    public static <I, O> Function<I, O> withDelay(Function<I, O> inner, Duration delay) {
        return withDelay(inner, delay, SpeedMultiplier.identity());
    }
}
