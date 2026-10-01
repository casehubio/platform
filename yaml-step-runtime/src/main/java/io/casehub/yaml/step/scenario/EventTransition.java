package io.casehub.yaml.step.scenario;

import java.util.List;
import java.util.Map;

public sealed interface EventTransition permits EventTransition.Simple, EventTransition.Guarded, EventTransition.MatchBased {

    String target();

    record Simple(String target) implements EventTransition {
        public Simple {
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("Event transition target must not be blank");
            }
        }
    }

    record Guarded(String target, String when) implements EventTransition {
        public Guarded {
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("Guarded event transition target must not be blank");
            }
            if (when == null || when.isBlank()) {
                throw new IllegalArgumentException("Guarded event transition 'when' must not be blank");
            }
        }
    }

    record MatchCase(Map<String, Object> match, String target) {
        public MatchCase {
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("Match case target must not be blank");
            }
            match = match != null ? Map.copyOf(match) : null;
        }
    }

    record MatchBased(List<MatchCase> cases) implements EventTransition {
        public MatchBased {
            if (cases == null || cases.isEmpty()) {
                throw new IllegalArgumentException("Match-based event transition must have at least one case");
            }
            cases = List.copyOf(cases);
        }

        @Override
        public String target() {
            return cases.getFirst().target();
        }
    }

    static EventTransition simple(String target) {
        return new Simple(target);
    }

    static EventTransition guarded(String target, String when) {
        return new Guarded(target, when);
    }
}
