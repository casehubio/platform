package io.casehub.platform.agent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BackendInstanceRegistry {
    void register(AgentBackend backend);
    Optional<AgentBackend> resolve(String key, String instanceId);
    List<AgentBackend> resolveByKey(String key);

    default Set<String> keys() {
        return Set.of();
    }
}
