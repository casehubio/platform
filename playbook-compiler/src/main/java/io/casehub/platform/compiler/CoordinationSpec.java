package io.casehub.platform.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import io.serverlessworkflow.impl.TaskContextData;
import io.casehub.platform.coordination.CoordinationPrimitive;
import io.casehub.platform.coordination.CoordinationStateStore;
import io.casehub.platform.coordination.DurableBarrier;
import io.casehub.platform.coordination.DurableChannel;
import io.casehub.platform.coordination.DurableCoordinationListener;
import io.casehub.platform.coordination.DurableQuorum;
import io.casehub.platform.coordination.DurableSagaTracker;
import io.casehub.platform.coordination.DurableSemaphore;
import io.casehub.platform.coordination.DurableSignal;
import io.casehub.platform.coordination.DurableStateMachine;

public record CoordinationSpec(List<PrimitiveSpec> primitives) {

    public static CoordinationSpec empty() {
        return new CoordinationSpec(List.of());
    }

    public boolean isEmpty() {
        return primitives.isEmpty();
    }

    public DurableCoordinationListener configure() {
        var listener = new DurableCoordinationListener(CoordinationStateStore.noOp());
        for (PrimitiveSpec spec : primitives) {
            listener.register(spec.toPrimitive());
        }
        return listener;
    }

    public sealed interface PrimitiveSpec {
        String name();
        CoordinationPrimitive toPrimitive();
    }

    public record BarrierSpec(String name, int required, TaskMatcher matcher) implements PrimitiveSpec {
        @Override
        public CoordinationPrimitive toPrimitive() {
            return new DurableBarrier(name, required, matcher.toPredicate());
        }
    }

    public record SemaphoreSpec(String name, int maxPermits, TaskMatcher matcher) implements PrimitiveSpec {
        @Override
        public CoordinationPrimitive toPrimitive() {
            return new DurableSemaphore(name, maxPermits, matcher.toPredicate());
        }
    }

    public record SignalSpec(String name, TaskMatcher sender, TaskMatcher receiver) implements PrimitiveSpec {
        @Override
        public CoordinationPrimitive toPrimitive() {
            return new DurableSignal(name, sender.toPredicate(), receiver.toPredicate());
        }
    }

    public record ChannelSpec(String name, int capacity,
            TaskMatcher producer, TaskMatcher consumer) implements PrimitiveSpec {
        @Override
        public CoordinationPrimitive toPrimitive() {
            return new DurableChannel(name, capacity, producer.toPredicate(), consumer.toPredicate());
        }
    }

    public record QuorumSpec(String name, int required, TaskMatcher matcher) implements PrimitiveSpec {
        @Override
        public CoordinationPrimitive toPrimitive() {
            return new DurableQuorum(name, required, matcher.toPredicate());
        }
    }

    public record StateMachineSpec(String name, String initialState,
            Map<String, Set<String>> transitions, TaskMatcher matcher) implements PrimitiveSpec {
        @Override
        public CoordinationPrimitive toPrimitive() {
            return new DurableStateMachine(name, initialState, transitions, matcher.toPredicate());
        }
    }

    public record SagaSpec(String name, List<String> stepOrder,
            Function<String, String> compensationNaming, TaskMatcher matcher) implements PrimitiveSpec {
        @Override
        public CoordinationPrimitive toPrimitive() {
            return new DurableSagaTracker(name, stepOrder, compensationNaming, matcher.toPredicate());
        }
    }

    public sealed interface TaskMatcher {
        Predicate<TaskContextData> toPredicate();

        record Prefix(String value) implements TaskMatcher {
            @Override
            public Predicate<TaskContextData> toPredicate() {
                return t -> t.taskName().startsWith(value);
            }
        }

        record Exact(String value) implements TaskMatcher {
            @Override
            public Predicate<TaskContextData> toPredicate() {
                return t -> value.equals(t.taskName());
            }
        }

        record AnyOf(List<String> values) implements TaskMatcher {
            @Override
            public Predicate<TaskContextData> toPredicate() {
                Set<String> set = Set.copyOf(values);
                return t -> set.contains(t.taskName());
            }
        }
    }

    static final class Builder {
        private final List<PrimitiveSpec> primitives = new ArrayList<>();

        void add(PrimitiveSpec spec) {
            primitives.add(spec);
        }

        CoordinationSpec build() {
            return new CoordinationSpec(List.copyOf(primitives));
        }
    }
}
