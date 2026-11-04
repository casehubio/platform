package io.casehub.platform.coordination.protocol;

import java.util.List;
import java.util.Map;

public class PlaybookProtocolEvaluator {

    public PlaybookProtocolEvaluator(io.casehub.platform.coordination.CoordinationStateStore stateStore) {
    }

    public void register(String protocolName, Map<String, Object> stateMap) {
    }

    public List<ProtocolAdvisory> evaluate(ProtocolInstanceKey key, String event, Map<String, Object> context) {
        return List.of();
    }

    public void evict(ProtocolInstanceKey key) {
    }
}
