package io.casehub.platform.coordination.protocol;

import io.casehub.yaml.core.orchestration.DefaultOrcStateMachine;
import io.casehub.yaml.core.orchestration.EventRouter;
import io.casehub.yaml.core.orchestration.OrcStateMachine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CompiledProtocol {

    private final String name;
    private final String initialState;
    private final Map<String, ProtocolAdvisory> advisoryMap;
    private final Map<String, Object> statesMap;

    private CompiledProtocol(String name, String initialState,
                             Map<String, ProtocolAdvisory> advisoryMap,
                             Map<String, Object> statesMap) {
        this.name = name;
        this.initialState = initialState;
        this.advisoryMap = Map.copyOf(advisoryMap);
        this.statesMap = statesMap;
    }

    @SuppressWarnings("unchecked")
    static CompiledProtocol from(String name, Map<String, Object> statesMap) {
        if (statesMap.isEmpty()) {
            throw new IllegalArgumentException("Protocol '" + name + "' has no states");
        }

        String initialState = statesMap.keySet().iterator().next();
        var advisories = new LinkedHashMap<String, ProtocolAdvisory>();

        for (var entry : statesMap.entrySet()) {
            String stateName = entry.getKey();
            List<Map<String, Object>> stateEntries = (List<Map<String, Object>>) entry.getValue();
            for (Map<String, Object> stateEntry : stateEntries) {
                if (stateEntry.containsKey("advisory")) {
                    @SuppressWarnings("unchecked")
                    var advMap = (Map<String, String>) stateEntry.get("advisory");
                    advisories.put(stateName, parseAdvisory(advMap, name, stateName));
                }
            }
        }

        return new CompiledProtocol(name, initialState, advisories, statesMap);
    }

    @SuppressWarnings("unchecked")
    private static DefaultOrcStateMachine.Builder<String> buildBuilder(String startState, Map<String, Object> statesMap) {
        var builder = DefaultOrcStateMachine.<String>builder(startState.intern(), startState.intern());
        for (var entry : statesMap.entrySet()) {
            String stateName = entry.getKey().intern();
            List<Map<String, Object>> stateEntries = (List<Map<String, Object>>) entry.getValue();
            for (Map<String, Object> stateEntry : stateEntries) {
                if (stateEntry.containsKey("on")) {
                    var eventMap = (Map<String, String>) stateEntry.get("on");
                    for (var ev : eventMap.entrySet()) {
                        builder.on(ev.getKey(), stateName, ev.getValue().intern());
                    }
                }
                if (stateEntry.containsKey("terminal")) {
                    builder.terminal(stateName);
                }
            }
        }
        return builder;
    }

    private static ProtocolAdvisory parseAdvisory(Map<String, String> map, String proto, String state) {
        String severityStr = map.get("severity");
        String message = map.get("message");
        String actionStr = map.get("action");

        if (severityStr == null || message == null || actionStr == null) {
            throw new IllegalArgumentException(
                "Protocol '" + proto + "' state '" + state + "': advisory requires severity, message, action");
        }

        AdvisorySeverity severity;
        try {
            severity = AdvisorySeverity.valueOf(severityStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Protocol '" + proto + "' state '" + state + "': unknown severity '" + severityStr + "'");
        }

        SuggestedAction action;
        try {
            action = SuggestedAction.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Protocol '" + proto + "' state '" + state + "': unknown action '" + actionStr + "'");
        }

        return new ProtocolAdvisory(severity, message, action);
    }

    String initialState() {
        return initialState;
    }

    Map<String, ProtocolAdvisory> advisoryMap() {
        return advisoryMap;
    }

    OrcStateMachine<String> buildStateMachine() {
        return buildStateMachine(initialState);
    }

    OrcStateMachine<String> buildStateMachine(String startState) {
        return buildBuilder(startState, statesMap).build();
    }

    EventRouter<String> buildRouter(OrcStateMachine<String> target) {
        return buildBuilder(target.currentState(), statesMap).buildRouter(target);
    }
}
