package io.casehub.platform.agent.geminicli;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentRuntime;
import io.casehub.platform.agent.BackendProvider;

import java.time.Duration;
import java.util.Map;

/** ServiceLoader-discovered factory for Gemini CLI backends. */
public class GeminiCliBackendProvider implements BackendProvider {

    @Override
    public String vendorKey() {
        return "gemini-cli";
    }

    @Override
    public AgentBackend create(Map<String, String> properties) {
        var runtime = createRuntime();
        return new GeminiCliAgentBackend(new GeminiCliAgentProperties() {
            @Override public String binaryPath() {
                return properties.getOrDefault("binary-path", "gemini");
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
                    "agent-runtime-core must be on the classpath for Gemini CLI backend", e);
        }
    }
}
