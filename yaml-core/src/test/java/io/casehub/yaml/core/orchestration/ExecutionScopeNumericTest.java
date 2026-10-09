package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionScopeNumericTest {

    @Test
    void numericPrimitiveReturnsCounter() {
        var scope = new DefaultExecutionScope();
        scope.counter("supply");
        OrcNumericPrimitive np = scope.numericPrimitive("supply");
        assertNotNull(np);
        assertInstanceOf(OrcCounter.class, np);
    }

    @Test
    void numericPrimitiveReturnsAccumulator() {
        var scope = new DefaultExecutionScope();
        scope.accumulator("total", Double::sum, 0.0);
        OrcNumericPrimitive np = scope.numericPrimitive("total");
        assertNotNull(np);
        assertInstanceOf(OrcAccumulator.class, np);
    }

    @Test
    void numericPrimitiveThrowsForNonNumeric() {
        var scope = new DefaultExecutionScope();
        scope.flag("ready");
        assertThrows(IllegalArgumentException.class,
                () -> scope.numericPrimitive("ready"));
    }

    @Test
    void numericPrimitiveThrowsForMissing() {
        var scope = new DefaultExecutionScope();
        assertThrows(IllegalArgumentException.class,
                () -> scope.numericPrimitive("nonexistent"));
    }

    @Test
    void prioritySemaphoreCreatesAndReturns() throws InterruptedException {
        var scope = new DefaultExecutionScope();
        var sem = scope.prioritySemaphore("minerals", 2);
        assertNotNull(sem);
        assertEquals(2, sem.availablePermits());
        sem.acquire(Priority.NORMAL);
        assertEquals(1, sem.availablePermits());
        sem.release();
    }

    @Test
    void prioritySemaphoreReusesExisting() {
        var scope = new DefaultExecutionScope();
        var sem1 = scope.prioritySemaphore("minerals", 1);
        var sem2 = scope.prioritySemaphore("minerals", 1);
        assertSame(sem1, sem2);
    }

    @Test
    void numericPrimitiveTraversesParentScope() {
        var parent = new DefaultExecutionScope();
        parent.counter("supply");
        var child = (DefaultExecutionScope) parent.childScope("child");
        OrcNumericPrimitive np = child.numericPrimitive("supply");
        assertNotNull(np);
        assertInstanceOf(OrcCounter.class, np);
    }
}
