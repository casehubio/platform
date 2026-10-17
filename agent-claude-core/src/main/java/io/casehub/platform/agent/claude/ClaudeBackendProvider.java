package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.BackendProvider;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** ServiceLoader-discovered factory for Claude CLI backends. */
public class ClaudeBackendProvider implements BackendProvider {

    @Override
    public String vendorKey() {
        return "claude";
    }

    @Override
    public AgentBackend create(Map<String, String> properties) {
        var client = new ClaudeAgentClient(new ClaudeAgentProperties() {
            @Override public Optional<String> binaryPath() {
                return Optional.ofNullable(properties.get("binary-path"));
            }
            @Override public Duration defaultTimeout() {
                return durationProperty(properties, "default-timeout", 300);
            }
            @Override public int maxConcurrentSessions() {
                return intProperty(properties, "max-concurrent-sessions", 2);
            }
        });
        return new ClaudeAgentProvider(client);
    }
}
