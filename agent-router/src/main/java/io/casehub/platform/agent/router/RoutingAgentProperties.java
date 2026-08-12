package io.casehub.platform.agent.router;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.platform.agent")
public interface RoutingAgentProperties {

    @WithDefault("claude")
    String defaultBackend();
}
