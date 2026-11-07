package io.casehub.platform.simulation.selector;

import io.casehub.platform.simulation.CorpusSelector;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class StateMachineSelector implements CorpusSelector {

    private final Map<String, Map<String, String>> transitions;
    private final AtomicReference<String> currentState;

    public StateMachineSelector(String initial, Map<String, Map<String, String>> transitions) {
        this.currentState = new AtomicReference<>(initial);
        this.transitions = Map.copyOf(transitions);
    }

    @Override
    public String activePhase() {
        return currentState.get();
    }

    public void advance(String trigger) {
        currentState.getAndUpdate(state -> {
            Map<String, String> stateTransitions = transitions.get(state);
            if (stateTransitions == null) return state;
            String next = stateTransitions.get(trigger);
            return next != null ? next : state;
        });
    }
}
