package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;

import static org.junit.jupiter.api.Assertions.*;

class OrcNumericPrimitiveTest {

    @Test
    void counterImplementsOrcNumericPrimitive() {
        var counter = new DefaultOrcCounter();
        assertInstanceOf(OrcNumericPrimitive.class, counter);
    }

    @Test
    void counterDoubleValueReturnsSum() {
        var counter = new DefaultOrcCounter();
        counter.add(42);
        assertEquals(42.0, counter.doubleValue());
    }

    @Test
    void counterListenerFiresOnIncrement() throws Exception {
        var counter = new DefaultOrcCounter();
        var future = new CompletableFuture<Double>();
        counter.onThresholdChange(future::complete);
        counter.increment();
        assertEquals(1.0, future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void counterListenerFiresOnAdd() throws Exception {
        var counter = new DefaultOrcCounter();
        var future = new CompletableFuture<Double>();
        counter.onThresholdChange(future::complete);
        counter.add(10);
        assertEquals(10.0, future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void counterRemoveListenerStopsNotification() {
        var counter = new DefaultOrcCounter();
        var called = new AtomicBoolean(false);
        DoubleConsumer listener = v -> called.set(true);
        counter.onThresholdChange(listener);
        counter.removeThresholdListener(listener);
        counter.increment();
        assertFalse(called.get());
    }

    @Test
    void accumulatorImplementsOrcNumericPrimitive() {
        var acc = new DefaultOrcAccumulator(Double::sum, 0.0);
        assertInstanceOf(OrcNumericPrimitive.class, acc);
    }

    @Test
    void accumulatorDoubleValueReturnsGet() {
        var acc = new DefaultOrcAccumulator(Double::sum, 0.0);
        acc.accumulate(3.14);
        assertEquals(3.14, acc.doubleValue(), 0.001);
    }

    @Test
    void accumulatorListenerFiresOnAccumulate() throws Exception {
        var acc = new DefaultOrcAccumulator(Double::sum, 0.0);
        var future = new CompletableFuture<Double>();
        acc.onThresholdChange(future::complete);
        acc.accumulate(5.0);
        assertEquals(5.0, future.get(1, TimeUnit.SECONDS), 0.001);
    }

    @Test
    void multipleListenersAllFire() throws Exception {
        var counter = new DefaultOrcCounter();
        var f1 = new CompletableFuture<Double>();
        var f2 = new CompletableFuture<Double>();
        counter.onThresholdChange(f1::complete);
        counter.onThresholdChange(f2::complete);
        counter.increment();
        assertEquals(1.0, f1.get(1, TimeUnit.SECONDS));
        assertEquals(1.0, f2.get(1, TimeUnit.SECONDS));
    }

    @Test
    void counterDecrementNotifiesListeners() throws Exception {
        var counter = new DefaultOrcCounter();
        counter.add(10);
        var future = new CompletableFuture<Double>();
        counter.onThresholdChange(future::complete);
        counter.decrement();
        assertEquals(9.0, future.get(1, TimeUnit.SECONDS));
    }
}
