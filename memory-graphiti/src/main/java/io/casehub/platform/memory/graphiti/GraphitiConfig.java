package io.casehub.platform.memory.graphiti;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.memory.graphiti")
public interface GraphitiConfig {

    /** Bearer token for Graphiti auth — omit if auth is not enabled on the Graphiti service. */
    Optional<String> apiKey();
}
