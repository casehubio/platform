package io.casehub.yaml.step.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class InlineDecoratorWrappersTest {

    @Test
    void timeoutPassesThroughWhenFast() {
        Function<String, String> wrapped = InlineDecoratorWrappers.withTimeout(
            String::toUpperCase, Duration.ofSeconds(5));
        assertEquals("HELLO", wrapped.apply("hello"));
    }

    @Test
    @Timeout(5)
    void timeoutThrowsWhenSlow() {
        Function<String, String> wrapped = InlineDecoratorWrappers.withTimeout(
            s -> {
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return s;
            }, Duration.ofMillis(50));
        assertThrows(RuntimeException.class, () -> wrapped.apply("hello"));
    }

    @Test
    void retryRetriesOnException() {
        AtomicInteger attempts = new AtomicInteger();
        Function<String, String> wrapped = InlineDecoratorWrappers.withRetry(
            s -> {
                if (attempts.incrementAndGet() < 3) throw new RuntimeException("fail");
                return s.toUpperCase();
            }, 3, Duration.ofMillis(10), "fixed");
        assertEquals("HELLO", wrapped.apply("hello"));
        assertEquals(3, attempts.get());
    }

    @Test
    void retryExhaustsAndThrows() {
        Function<String, String> wrapped = InlineDecoratorWrappers.withRetry(
            s -> { throw new RuntimeException("always fails"); },
            2, Duration.ofMillis(10), "fixed");
        assertThrows(RuntimeException.class, () -> wrapped.apply("hello"));
    }

    @Test
    void delayPausesBeforeExecution() {
        long start = System.nanoTime();
        Function<String, String> wrapped = InlineDecoratorWrappers.withDelay(
            String::toUpperCase, Duration.ofMillis(100));
        wrapped.apply("hello");
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed >= 80, "Should delay at least 80ms, was " + elapsed);
    }

    @Test
    void computeDelayFixed() {
        assertEquals(100, InlineDecoratorWrappers.computeDelay(100, "fixed", 0));
        assertEquals(100, InlineDecoratorWrappers.computeDelay(100, "fixed", 3));
    }

    @Test
    void computeDelayExponential() {
        assertEquals(100, InlineDecoratorWrappers.computeDelay(100, "exponential", 0));
        assertEquals(200, InlineDecoratorWrappers.computeDelay(100, "exponential", 1));
        assertEquals(400, InlineDecoratorWrappers.computeDelay(100, "exponential", 2));
    }

    @Test
    void speedMultiplierAffectsDelay() {
        long start = System.nanoTime();
        Function<String, String> wrapped = InlineDecoratorWrappers.withDelay(
            String::toUpperCase, Duration.ofMillis(200), () -> 10.0);
        wrapped.apply("hello");
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed < 100, "10x speed should reduce 200ms to ~20ms, was " + elapsed);
    }
}
