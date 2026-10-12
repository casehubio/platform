package io.casehub.platform.registry.client.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.registry.client")
public interface RegistryClientConfig {

    Optional<String> url();

    Optional<String> entryId();

    @WithDefault("service")
    String entryType();

    @WithDefault("default")
    String namespace();

    Optional<String> tenancyId();

    @WithDefault("30")
    int ttlSeconds();

    @WithDefault("10s")
    String heartbeatInterval();
}
