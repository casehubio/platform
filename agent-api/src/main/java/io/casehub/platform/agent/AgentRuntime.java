package io.casehub.platform.agent;

import java.io.IOException;

/**
 * SPI for agent process execution environments. CLI agent backends inject this
 * to spawn agent processes without coupling to the execution environment
 * (local subprocess, K8s pod, container).
 */
public interface AgentRuntime {

    AgentProcess spawn(AgentRuntimeConfig config) throws IOException;
}
