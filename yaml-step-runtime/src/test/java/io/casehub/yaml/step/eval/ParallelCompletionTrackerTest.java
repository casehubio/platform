package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.orchestration.DefaultExecutionScope;
import io.casehub.yaml.core.orchestration.OrcLatch;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelCompletionTrackerTest {

    private final DefaultExecutionScope scope = new DefaultExecutionScope();

    private ParallelCompletionTracker tracker(int required, int total) {
        OrcLatch advancement = scope.latch("adv-" + required + "-" + total, required);
        OrcLatch completion = scope.latch("comp-" + required + "-" + total, total);
        return new ParallelCompletionTracker(
            advancement, completion, required, total,
            new AtomicInteger(), new AtomicInteger(),
            new AtomicBoolean(), new AtomicReference<>());
    }

    @Test
    void success_countsDownAdvancementLatch() {
        var t = tracker(1, 2);
        t.onStepComplete(true, Result.of(Map.of("winner", true)));
        assertThat(t.advancementLatch().getCount()).isZero();
        assertThat(t.completionLatch().getCount()).isEqualTo(1);
        assertThat(t.advancingResult().get().isSuccess()).isTrue();
    }

    @Test
    void failure_doesNotCountDownAdvancementLatch() {
        var t = tracker(1, 2);
        t.onStepComplete(false, Result.failed("boom"));
        assertThat(t.advancementLatch().getCount()).isEqualTo(1);
        assertThat(t.completionLatch().getCount()).isEqualTo(1);
    }

    @Test
    void unreachableThreshold_forceDrainsAdvancementLatch() {
        var t = tracker(2, 3);
        t.onStepComplete(false, Result.failed("fail-1"));
        t.onStepComplete(false, Result.failed("fail-2"));
        assertThat(t.isUnreachable()).isTrue();
        assertThat(t.advancementLatch().getCount()).isZero();
    }

    @Test
    void unreachableThreshold_exactBoundary() {
        var t = tracker(2, 3);
        t.onStepComplete(false, Result.failed("fail-1"));
        assertThat(t.isUnreachable()).isFalse();
        t.onStepComplete(false, Result.failed("fail-2"));
        assertThat(t.isUnreachable()).isTrue();
    }

    @Test
    void advancingResult_capturedOnNthSuccess() {
        var t = tracker(2, 3);
        t.onStepComplete(true, Result.of(Map.of("first", true)));
        assertThat(t.advancementLatch().getCount()).isEqualTo(1);
        assertThat(t.advancingResult().get()).isNull();

        t.onStepComplete(true, Result.of(Map.of("second", true)));
        assertThat(t.advancementLatch().getCount()).isZero();
        assertThat(t.advancingResult().get().output())
            .containsEntry("second", true);
    }

    @Test
    void completionLatch_countsDownOnBothSuccessAndFailure() {
        var t = tracker(1, 3);
        t.onStepComplete(true, Result.of(Map.of()));
        t.onStepComplete(false, Result.failed("boom"));
        t.onStepComplete(true, Result.of(Map.of()));
        assertThat(t.completionLatch().getCount()).isZero();
    }

    @Test
    void mixedSuccessAndFailure_advancesCorrectly() {
        var t = tracker(1, 3);
        t.onStepComplete(false, Result.failed("fail"));
        assertThat(t.advancementLatch().getCount()).isEqualTo(1);
        t.onStepComplete(true, Result.of(Map.of("winner", true)));
        assertThat(t.advancementLatch().getCount()).isZero();
        assertThat(t.advancingResult().get().output())
            .containsEntry("winner", true);
    }
}
