package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.orchestration.OrcLatch;
import io.casehub.yaml.plugin.api.Result;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

record ParallelCompletionTracker(OrcLatch advancementLatch, OrcLatch completionLatch,
                                  int required, int totalSteps,
                                  AtomicInteger successCount, AtomicInteger failureCount,
                                  AtomicBoolean unreachable,
                                  AtomicReference<Result> advancingResult) {

    void onStepComplete(boolean success, Result result) {
        if (success) {
            int count = successCount.incrementAndGet();
            if (count == required) {
                advancingResult.compareAndSet(null, result);
            }
            advancementLatch.countDown();
        } else {
            int failures = failureCount.incrementAndGet();
            if (failures > totalSteps - required) {
                unreachable.set(true);
                while (advancementLatch.getCount() > 0) {
                    advancementLatch.countDown();
                }
            }
        }
        completionLatch.countDown();
    }

    boolean isUnreachable() {
        return unreachable.get();
    }
}
