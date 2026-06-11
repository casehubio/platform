package io.casehub.platform.memory.graphiti;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.memory.graphiti")
public interface GraphitiConfig {

    /** Bearer token for Graphiti auth — omit if auth is not enabled on the Graphiti service. */
    Optional<String> apiKey();

    /**
     * Comma-separated list of domain names that this Graphiti deployment stores.
     * Required to enable {@link io.casehub.platform.api.memory.MemoryCapability#ERASE_ENTITY}.
     * When absent, {@code eraseEntity()} throws {@link io.casehub.platform.api.memory.MemoryCapabilityException}.
     */
    @WithName("known-domains")
    Optional<List<String>> knownDomains();
}
