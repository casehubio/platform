package io.casehub.yaml.core.orchestration;

import io.casehub.yaml.core.step.MatchPattern;

import java.util.List;
import java.util.function.Predicate;

public class EventRouter<S extends Enum<S>> {
    private final OrcStateMachine<S>    target;
    private final List<EventMapping<S>> mappings;

    EventRouter(OrcStateMachine<S> target, List<EventMapping<S>> mappings) {
        this.target   = target;
        this.mappings = List.copyOf(mappings);
    }

    public boolean fire(String event)                 {return fire(event, null);}

    public boolean fire(String event, Object context) {return fire((Object) event, context);}

    public boolean fire(Object event, Object context) {
        S      current     = target.currentState();
        String currentName = current.name();
        for (var m : mappings) {
            if (m.onPattern().matches(event) && m.fromPattern().matches(currentName)) {
                if (m.guard() == null || m.guard().test(context)) {
                    return target.transition(current, m.to(), context);
                }
            }
        }
        return false;
    }

    public EventRouter<S> targeting(OrcStateMachine<S> newTarget) {
        return new EventRouter<>(newTarget, this.mappings);
    }

    public record EventMapping<S>(MatchPattern fromPattern, S to, MatchPattern onPattern, Predicate<Object> guard) {}
}
