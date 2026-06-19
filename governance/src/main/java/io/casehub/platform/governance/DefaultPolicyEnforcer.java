package io.casehub.platform.governance;

import io.casehub.platform.api.governance.BackoffStrategy;
import io.casehub.platform.api.governance.ExecutionPolicy;
import io.casehub.platform.api.governance.RetryPolicy;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Blocking policy enforcer — applies retry, timeout, and backoff policies.
 *
 * <p>This enforcer uses {@link Thread#sleep} for backoff delays and submits
 * timed work to a shared thread pool. It must <b>not</b> be called from
 * Vert.x event-loop threads. Callers (e.g. WorkerExecutor in casehub-worker)
 * are responsible for ensuring execution happens on a worker thread.</p>
 */
@ApplicationScoped
public class DefaultPolicyEnforcer implements PolicyEnforcer {

    private final ExecutorService timeoutExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void shutdown() {
        timeoutExecutor.shutdownNow();
    }

    @Override
    public <T> T execute(ExecutionPolicy policy, Supplier<T> action) {
        int maxAttempts = 1;
        int delayMs = 0;
        BackoffStrategy backoff = BackoffStrategy.FIXED;
        Integer maxDelayMs = null;

        if (policy.retries() != null) {
            RetryPolicy retry = policy.retries();
            if (retry.maxAttempts() != null) maxAttempts = retry.maxAttempts();
            if (retry.delayMs() != null) delayMs = retry.delayMs();
            if (retry.backoffStrategy() != null) backoff = retry.backoffStrategy();
            maxDelayMs = retry.maxDelayMs();
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeWithTimeout(policy.timeoutMs(), action);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    sleep(computeDelay(delayMs, backoff, attempt, maxDelayMs));
                }
            }
        }
        throw new PolicyEnforcementException(
            "All " + maxAttempts + " attempts failed", lastException);
    }

    private <T> T executeWithTimeout(Integer timeoutMs, Supplier<T> action) {
        if (timeoutMs == null) {
            return action.get();
        }
        Callable<T> callable = action::get;
        Future<T> future = timeoutExecutor.submit(callable);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PolicyEnforcementException(
                "Action timed out after " + timeoutMs + "ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new PolicyEnforcementException("Action failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PolicyEnforcementException("Interrupted during execution", e);
        }
    }

    private long computeDelay(int baseDelayMs, BackoffStrategy strategy, int attempt, Integer maxDelayMs) {
        long delay = switch (strategy) {
            case FIXED -> baseDelayMs;
            case EXPONENTIAL -> (long) (baseDelayMs * Math.pow(2, attempt - 1));
            case EXPONENTIAL_WITH_JITTER -> {
                long exponential = (long) (baseDelayMs * Math.pow(2, attempt - 1));
                yield exponential + ThreadLocalRandom.current().nextLong(exponential / 2 + 1);
            }
        };
        if (maxDelayMs != null && delay > maxDelayMs) {
            delay = maxDelayMs;
        }
        return delay;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
