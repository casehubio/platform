package io.casehub.yaml.core.orchestration;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class EventRouter<S extends Enum<S>> {
    private final OrcStateMachine<S> target;
    private final Map<String, List<EventMapping<S>>> mappings;

    EventRouter(OrcStateMachine<S> target, Map<String, List<EventMapping<S>>> mappings) {
        this.target = target;
        this.mappings = Map.copyOf(mappings);
    }

    public boolean fire(String event) { return fire(event, null); }

    public boolean fire(String event, Object context) {
        var candidates = mappings.get(event);
        if (candidates == null) return false;
        S current = target.currentState();
        for (var m : candidates) {
            if (m.from() == current) {
                if (m.guard() == null || m.guard().test(context)) {
                    return target.transition(m.from(), m.to(), context);
                }
            }
        }
        return false;
    }

    public EventRouter<S> targeting(OrcStateMachine<S> newTarget) {
        return new EventRouter<>(newTarget, this.mappings);
    }

    public record EventMapping<S>(S from, S to, Predicate<Object> guard) {}
}
