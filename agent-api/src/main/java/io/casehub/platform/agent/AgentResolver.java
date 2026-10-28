package io.casehub.platform.agent;

import java.util.List;
import java.util.Optional;

public interface AgentResolver {

    Optional<ResolvedAgent> resolve(String agentId);

    List<AgentDescriptor> discover();
}
