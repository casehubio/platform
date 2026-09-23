package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Showcase: barrier and quorum patterns.
 *
 * Equivalent YAML:
 * <pre>
 * - parallel:
 *     - step: momentum-eval
 *       action: evaluate-momentum
 *     - step: risk-eval
 *       action: evaluate-risk
 *     - step: compliance-check
 *       action: check-compliance
 *
 * - step: await-all
 *   barrier:
 *     await: [momentum-eval, risk-eval, compliance-check]
 *     timeout: 30s
 *
 * - step: consensus
 *   quorum:
 *     required: 2
 *     of: [momentum-eval, risk-eval, compliance-check]
 *     timeout: 15s
 * </pre>
 */
class ShowcaseBarrierQuorumTest {

    @Test
    void barrier_awaitsAllParallelSteps() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var barrier = scope.latch("await-all", 3);
        var results = scope.resultStore();
        var allDone = new CountDownLatch(3);

        // Simulate 3 parallel evaluation steps
        Thread.ofVirtual().name("momentum-eval").start(() -> {
            results.recordSuccess("momentum-eval", Map.of("score", 0.8));
            barrier.countDown();
            allDone.countDown();
        });
        Thread.ofVirtual().name("risk-eval").start(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { return; }
            results.recordSuccess("risk-eval", Map.of("score", 0.6));
            barrier.countDown();
            allDone.countDown();
        });
        Thread.ofVirtual().name("compliance-check").start(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            results.recordSuccess("compliance-check", Map.of("passed", true));
            barrier.countDown();
            allDone.countDown();
        });

        // Barrier waits for all 3
        boolean completed = barrier.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        allDone.await(5, TimeUnit.SECONDS);

        // All results available
        assertThat(results.result("momentum-eval")).containsEntry("score", 0.8);
        assertThat(results.result("risk-eval")).containsEntry("score", 0.6);
        assertThat(results.result("compliance-check")).containsEntry("passed", true);

        scope.close();
    }

    @Test
    void quorum_proceedsAfterTwoOfThree() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var quorum = scope.latch("consensus", 2); // required: 2
        var results = scope.resultStore();

        // Two fast evaluations, one slow
        Thread.ofVirtual().name("fast-a").start(() -> {
            results.recordSuccess("strategy-a", Map.of("vote", "BUY"));
            quorum.countDown();
        });
        Thread.ofVirtual().name("fast-b").start(() -> {
            results.recordSuccess("strategy-b", Map.of("vote", "BUY"));
            quorum.countDown();
        });
        Thread.ofVirtual().name("slow-c").start(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            results.recordSuccess("strategy-c", Map.of("vote", "SELL"));
            quorum.countDown();
        });

        // Quorum met after 2 — doesn't wait for the slow one
        boolean met = quorum.await(2, TimeUnit.SECONDS);
        assertThat(met).isTrue();
        assertThat(results.hasCompleted("strategy-a")).isTrue();
        assertThat(results.hasCompleted("strategy-b")).isTrue();
        // strategy-c may or may not have completed — quorum doesn't wait

        scope.close();
    }
}
