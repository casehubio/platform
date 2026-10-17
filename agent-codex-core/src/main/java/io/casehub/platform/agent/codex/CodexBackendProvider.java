package io.casehub.platform.agent.codex;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentRuntime;
import io.casehub.platform.agent.BackendProvider;

import java.time.Duration;
import java.util.Map;

/** ServiceLoader-discovered factory for Codex CLI backends. */
public class CodexBackendProvider implements BackendProvider {

    @Override
    public String vendorKey() {
        return "codex";
    }

    @Override
    public AgentBackend create(Map<String, String> properties) {
        var runtime = createRuntime();
        return new CodexAgentBackend(new CodexAgentProperties() {
            @Override public String binaryPath() {
                return properties.getOrDefault("binary-path", "codex");
            }
            @Override public Duration defaultTimeout() {
                return durationProperty(properties, "default-timeout", 120);
            }
            @Override public int maxConcurrentSessions() {
                return intProperty(properties, "max-concurrent-sessions", 4);
            }
        }, runtime);
    }

    private AgentRuntime createRuntime() {
        try {
            var clazz = Class.forName("io.casehub.platform.agent.runtime.SubprocessRuntime");
            return (AgentRuntime) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "agent-runtime-core must be on the classpath for Codex CLI backend", e);
        }
    }
}
