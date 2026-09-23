package io.casehub.yaml.core.orchestration;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class DefaultOrcStateMachine<S extends Enum<S>> implements OrcStateMachine<S> {

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
        String key = from.name() + "→" + to.name();
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
        String key = from.name() + "→" + to.name();
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

    public static <S extends Enum<S>> Builder<S> builder(String name, Class<S> stateType, S initialState) {
        return new Builder<>(name, stateType, initialState);
    }

    public static final class Builder<S extends Enum<S>> {
        private final String name;
        private final Class<S> stateType;
        private final S initialState;
        private final Map<S, Map<S, Predicate<Object>>> transitions = new java.util.HashMap<>();
        private final Set<S> terminalStates;
        private final Map<String, java.util.List<EventRouter.EventMapping<S>>> eventMappings = new java.util.HashMap<>();

        private Builder(String name, Class<S> stateType, S initialState) {
            this.name = name;
            this.stateType = stateType;
            this.initialState = initialState;
            this.terminalStates = EnumSet.noneOf(stateType);
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
            eventMappings.computeIfAbsent(event, k -> new java.util.ArrayList<>())
                         .add(new EventRouter.EventMapping<>(from, to, null));
            return this;
        }

        public Builder<S> on(String event, S from, S to, Predicate<Object> guard) {
            transition(from, to, guard);
            eventMappings.computeIfAbsent(event, k -> new java.util.ArrayList<>())
                         .add(new EventRouter.EventMapping<>(from, to, guard));
            return this;
        }

        public EventRouter<S> buildRouter(OrcStateMachine<S> target) {
            return new EventRouter<>(target, Map.copyOf(eventMappings));
        }

    }
}
