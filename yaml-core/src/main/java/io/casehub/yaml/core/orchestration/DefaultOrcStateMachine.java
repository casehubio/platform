package io.casehub.yaml.core.orchestration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class DefaultOrcStateMachine<S> implements OrcStateMachine<S> {

    private final String name;
    private final AtomicReference<S> state;
    private final Map<S, Map<S, Predicate<Object>>> transitions;
    private final Set<S> terminalStates;
    private final Map<String, List<TransitionHandler>> transitionHandlers;
    private final Map<S, List<StateHandler>> enterHandlers;
    private final Map<S, List<StateHandler>> exitHandlers;

    private DefaultOrcStateMachine(String name, S initialState,
                                    Map<S, Map<S, Predicate<Object>>> transitions,
                                    Set<S> terminalStates) {
        this.name = name;
        this.state = new AtomicReference<>(initialState);
        this.transitions = transitions;
        this.terminalStates = terminalStates;
        this.transitionHandlers = new ConcurrentHashMap<>();
        this.enterHandlers = new ConcurrentHashMap<>();
        this.exitHandlers = new ConcurrentHashMap<>();
    }

    @Override
    public S currentState() {
        return state.get();
    }

    @Override
    public boolean transition(S from, S to) {
        return transition(from, to, null);
    }

    @Override
    public boolean transition(S from, S to, Object payload) {
        if (terminalStates.contains(from)) {
            throw new IllegalTransitionException(name, from, "terminal state — no transitions allowed");
        }

        Map<S, Predicate<Object>> fromTransitions = transitions.get(from);
        if (fromTransitions == null || !fromTransitions.containsKey(to)) {
            throw new IllegalTransitionException(name, from, to);
        }

        Predicate<Object> guard = fromTransitions.get(to);
        if (guard != null && !guard.test(payload)) {
            return false;
        }

        if (!state.compareAndSet(from, to)) {
            return false;
        }

        fireExitHandlers(from);
        fireTransitionHandlers(from, to, payload);
        fireEnterHandlers(to);

        return true;
    }

    @Override
    public void onTransition(S from, S to, TransitionHandler handler) {
        String key = String.valueOf(from) + "→" + String.valueOf(to);
        transitionHandlers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void onEnter(S state, StateHandler handler) {
        enterHandlers.computeIfAbsent(state, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void onExit(S state, StateHandler handler) {
        exitHandlers.computeIfAbsent(state, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    private void fireTransitionHandlers(S from, S to, Object payload) {
        String key = String.valueOf(from) + "→" + String.valueOf(to);
        List<TransitionHandler> handlers = transitionHandlers.get(key);
        if (handlers != null) {
            for (TransitionHandler h : handlers) {
                h.onTransition(payload);
            }
        }
    }

    private void fireEnterHandlers(S state) {
        List<StateHandler> handlers = enterHandlers.get(state);
        if (handlers != null) {
            for (StateHandler h : handlers) {
                h.onState();
            }
        }
    }

    private void fireExitHandlers(S state) {
        List<StateHandler> handlers = exitHandlers.get(state);
        if (handlers != null) {
            for (StateHandler h : handlers) {
                h.onState();
            }
        }
    }


    public static <S> Builder<S> builder(String name, S initialState) {
        return new Builder<>(name, initialState);
    }

    public static <S> Builder<S> builder(String name, Class<S> stateType, S initialState) {
        return new Builder<>(name, initialState);
    }

    public static final class Builder<S> {
        private final String                                      name;

        private final S                                           initialState;
        private final Map<S, Map<S, Predicate<Object>>>           transitions   = new java.util.HashMap<>();
        private final Set<S>                                      terminalStates;
        private final java.util.List<EventRouter.EventMapping<S>> eventMappings = new java.util.ArrayList<>();

        private Builder(String name, S initialState) {
            this.name           = name;
            this.initialState   = initialState;
            this.terminalStates = new java.util.HashSet<>();
        }

        public Builder<S> transition(S from, S to) {
            transitions.computeIfAbsent(from, k -> new java.util.HashMap<>()).put(to, null);
            return this;
        }

        public Builder<S> transition(S from, S to, Predicate<Object> guard) {
            transitions.computeIfAbsent(from, k -> new java.util.HashMap<>()).put(to, guard);
            return this;
        }

        public Builder<S> terminal(S... states) {
            for (S s : states) {
                terminalStates.add(s);
            }
            return this;
        }

        public DefaultOrcStateMachine<S> build() {
            return new DefaultOrcStateMachine<>(name, initialState, Map.copyOf(transitions), Set.copyOf(terminalStates));
        }

        public Builder<S> on(String event, S from, S to) {
            transition(from, to);
            eventMappings.add(new EventRouter.EventMapping<>(
                    new io.casehub.yaml.core.step.MatchPattern.ValuePattern(String.valueOf(from)), to,
                    new io.casehub.yaml.core.step.MatchPattern.ValuePattern(event), null));
            return this;
        }

        public Builder<S> on(String event, S from, S to, Predicate<Object> guard) {
            transition(from, to, guard);
            eventMappings.add(new EventRouter.EventMapping<>(
                    new io.casehub.yaml.core.step.MatchPattern.ValuePattern(String.valueOf(from)), to,
                    new io.casehub.yaml.core.step.MatchPattern.ValuePattern(event), guard));
            return this;
        }

        public Builder<S> on(io.casehub.yaml.core.step.MatchPattern onPattern,
                             io.casehub.yaml.core.step.MatchPattern fromPattern, S to) {
            return on(onPattern, fromPattern, to, null);
        }

        public Builder<S> on(io.casehub.yaml.core.step.MatchPattern onPattern,
                             io.casehub.yaml.core.step.MatchPattern fromPattern, S to,
                             Predicate<Object> guard) {
            registerTransitionsForPattern(fromPattern, to, guard);
            eventMappings.add(new EventRouter.EventMapping<>(fromPattern, to, onPattern, guard));
            return this;
        }

        private void registerTransitionsForPattern(io.casehub.yaml.core.step.MatchPattern fromPattern,
                                                   S to, Predicate<Object> guard) {
            Set<S> knownStates = new java.util.HashSet<>();
            transitions.keySet().forEach(knownStates::add);
            transitions.values().forEach(m -> knownStates.addAll(m.keySet()));
            knownStates.addAll(terminalStates);

            switch (fromPattern) {
                case io.casehub.yaml.core.step.MatchPattern.ValuePattern(var value) -> {
                    for (S s : knownStates) {
                        if (String.valueOf(s).equals(String.valueOf(value))) {
                            transitions.computeIfAbsent(s, k -> new java.util.HashMap<>()).put(to, guard);
                            break;
                        }
                    }
                }
                case io.casehub.yaml.core.step.MatchPattern.AnyOfPattern(var values) -> {
                    for (Object v : values) {
                        String vStr = String.valueOf(v);
                        for (S s : knownStates) {
                            if (String.valueOf(s).equals(vStr)) {
                                transitions.computeIfAbsent(s, k -> new java.util.HashMap<>()).put(to, guard);
                                break;
                            }
                        }
                    }
                }
                case io.casehub.yaml.core.step.MatchPattern.DefaultPattern() -> {
                    for (S s : knownStates) {
                        if (!terminalStates.contains(s)) {
                            transitions.computeIfAbsent(s, k -> new java.util.HashMap<>()).put(to, guard);
                        }
                    }
                }
                case io.casehub.yaml.core.step.MatchPattern.StructuralPattern sp -> {
                    for (S s : knownStates) {
                        if (!terminalStates.contains(s)) {
                            transitions.computeIfAbsent(s, k -> new java.util.HashMap<>()).put(to, guard);
                        }
                    }
                }
            }
        }

        public EventRouter<S> buildRouter(OrcStateMachine<S> target) {
            return new EventRouter<>(target, java.util.List.copyOf(eventMappings));
        }
    }
}
