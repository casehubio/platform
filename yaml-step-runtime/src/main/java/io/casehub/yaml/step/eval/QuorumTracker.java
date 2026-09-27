package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.orchestration.OrcLatch;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

record QuorumTracker(OrcLatch latch, int required, int totalSteps,
                     AtomicInteger successCount, AtomicInteger failureCount,
                     AtomicBoolean unreachable) {

    void onStepComplete(boolean success) {
        if (success) {
            successCount.incrementAndGet();
            latch.countDown();
            return;
        }
        int failures = failureCount.incrementAndGet();
        if (failures > totalSteps - required) {
            unreachable.set(true);
            while (latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }

    boolean isUnreachable() {
        return unreachable.get();
    }
}
