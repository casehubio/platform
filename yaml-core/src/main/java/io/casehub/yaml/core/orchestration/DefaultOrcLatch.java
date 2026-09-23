package io.casehub.yaml.core.orchestration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DefaultOrcLatch implements OrcLatch {

    private final CountDownLatch latch;

    public DefaultOrcLatch(int count) {
        this.latch = new CountDownLatch(count);
    }

    @Override
    public void countDown() {
        latch.countDown();
    }

    @Override
    public void await() throws InterruptedException {
        latch.await();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    @Override
    public long getCount() {
        return latch.getCount();
    }

    @Override
    public void releaseForClose() {
        while (getCount() > 0) {
            countDown();
        }
    }
}
