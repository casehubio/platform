package io.casehub.yaml.core.orchestration;

import java.util.concurrent.TimeUnit;

public interface PriorityOrcSemaphore extends OrcPrimitive {
    void acquire(Priority priority) throws InterruptedException;
    boolean tryAcquire(Priority priority, long timeout, TimeUnit unit) throws InterruptedException;
    void release();
    int availablePermits();
}
