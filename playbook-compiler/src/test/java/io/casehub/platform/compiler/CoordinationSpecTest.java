package io.casehub.platform.compiler;

import java.util.List;
import io.casehub.platform.coordination.DurableBarrier;
import io.casehub.platform.coordination.DurableSemaphore;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinationSpecTest {

    @Test
    void configure_registersLocalCoordinationPrimitives() {
        var matcher = new CoordinationSpec.TaskMatcher.Prefix("work");
        var spec = new CoordinationSpec(List.of(
                new CoordinationSpec.BarrierSpec("sync", 3, matcher),
                new CoordinationSpec.SemaphoreSpec("mutex", 1, matcher)));

        WorkflowExecutionCompletableListener listener = spec.configure();
        var coordination = (io.casehub.platform.coordination.DurableCoordinationListener) listener;
        assertThat(coordination.<DurableBarrier>primitive("sync")).isInstanceOf(DurableBarrier.class);
        assertThat(coordination.<DurableSemaphore>primitive("mutex")).isInstanceOf(DurableSemaphore.class);
    }

    @Test
    void empty_hasNoPrimitives() {
        CoordinationSpec spec = CoordinationSpec.empty();
        assertThat(spec.isEmpty()).isTrue();
        assertThat(spec.primitives()).isEmpty();
    }

    @Test
    void barrierSpec_hasCorrectFields() {
        var matcher = new CoordinationSpec.TaskMatcher.Prefix("work");
        var spec = new CoordinationSpec(List.of(
                new CoordinationSpec.BarrierSpec("sync", 3, matcher)));

        assertThat(spec.isEmpty()).isFalse();
        assertThat(spec.primitives()).hasSize(1);

        var barrier = spec.primitives().getFirst();
        assertThat(barrier.name()).isEqualTo("sync");
        assertThat(barrier).isInstanceOf(CoordinationSpec.BarrierSpec.class);
    }

    @Test
    void semaphoreSpec_hasCorrectFields() {
        var matcher = new CoordinationSpec.TaskMatcher.Exact("step_0");
        var spec = new CoordinationSpec(List.of(
                new CoordinationSpec.SemaphoreSpec("mutex", 1, matcher)));

        var sem = (CoordinationSpec.SemaphoreSpec) spec.primitives().getFirst();
        assertThat(sem.name()).isEqualTo("mutex");
        assertThat(sem.maxPermits()).isEqualTo(1);
    }

    @Test
    void signalSpec_hasSenderAndReceiver() {
        var sender = new CoordinationSpec.TaskMatcher.Prefix("send_");
        var receiver = new CoordinationSpec.TaskMatcher.Prefix("recv_");
        var spec = new CoordinationSpec(List.of(
                new CoordinationSpec.SignalSpec("go", sender, receiver)));

        var signal = (CoordinationSpec.SignalSpec) spec.primitives().getFirst();
        assertThat(signal.name()).isEqualTo("go");
        assertThat(signal.sender()).isEqualTo(sender);
        assertThat(signal.receiver()).isEqualTo(receiver);
    }

    @Test
    void taskMatcher_prefix() {
        var matcher = new CoordinationSpec.TaskMatcher.Prefix("work");
        assertThat(matcher.value()).isEqualTo("work");
    }

    @Test
    void taskMatcher_exact() {
        var matcher = new CoordinationSpec.TaskMatcher.Exact("step_0");
        assertThat(matcher.value()).isEqualTo("step_0");
    }

    @Test
    void taskMatcher_anyOf() {
        var matcher = new CoordinationSpec.TaskMatcher.AnyOf(List.of("a", "b", "c"));
        assertThat(matcher.values()).containsExactly("a", "b", "c");
    }

    @Test
    void builder_accumulatesSpecs() {
        var builder = new CoordinationSpec.Builder();
        builder.add(new CoordinationSpec.BarrierSpec("b1", 2,
                new CoordinationSpec.TaskMatcher.Prefix("work")));
        builder.add(new CoordinationSpec.SemaphoreSpec("s1", 3,
                new CoordinationSpec.TaskMatcher.Exact("task")));

        CoordinationSpec spec = builder.build();
        assertThat(spec.primitives()).hasSize(2);
    }
}
