package io.casehub.yaml.core.orchestration;

import java.util.concurrent.TimeUnit;

public interface OrcSemaphore extends OrcPrimitive {
    void acquire() throws InterruptedException;
    boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException;
    void release();
    int availablePermits();
}
