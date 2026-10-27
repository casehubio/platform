package io.casehub.platform.coordination.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.coordination.CoordinationStateStore;
import io.casehub.yaml.core.orchestration.OrcStateMachine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlaybookProtocolEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, CompiledProtocol> protocols = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ProtocolInstanceKey, ProtocolInstance> instances = new ConcurrentHashMap<>();
    private final CoordinationStateStore store;

    public PlaybookProtocolEvaluator(CoordinationStateStore store) {
        this.store = store;
    }

    public void register(String protocolName, Map<String, Object> statesMap) {
        protocols.put(protocolName, CompiledProtocol.from(protocolName, statesMap));
    }

    public List<ProtocolAdvisory> evaluate(ProtocolInstanceKey key, String event, Map<String, Object> context) {
        ProtocolInstance instance = instances.computeIfAbsent(key, this::createInstance);
        String stateBefore = instance.currentState();
        instance.fire(event, context);
        String stateAfter = instance.currentState();

        if (!stateBefore.equals(stateAfter)) {
            store.save(key.storeKey(), instance.serialize());
        }

        return instance.advisories();
    }

    public void evict(ProtocolInstanceKey key) {
        instances.remove(key);
    }

    private ProtocolInstance createInstance(ProtocolInstanceKey key) {
        CompiledProtocol protocol = protocols.get(key.protocolName());
        if (protocol == null) {
            throw new IllegalArgumentException("Unknown protocol: " + key.protocolName());
        }
        String savedState = loadSavedState(key.storeKey());
        OrcStateMachine<String> sm = savedState != null
            ? protocol.buildStateMachine(savedState)
            : protocol.buildStateMachine();
        var router = protocol.buildRouter(sm);
        return new ProtocolInstance(sm, router, protocol.advisoryMap());
    }

    @SuppressWarnings("unchecked")
    private String loadSavedState(String storeKey) {
        return store.load(storeKey).map(json -> {
            try {
                Map<String, Object> map = MAPPER.readValue(json, Map.class);
                return (String) map.get("current");
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }
}
