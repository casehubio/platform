package io.casehub.yaml.core.orchestration;

import java.util.concurrent.TimeUnit;

public interface OrcLatch extends OrcPrimitive {
    void countDown();
    void await() throws InterruptedException;
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;
    long getCount();
}
