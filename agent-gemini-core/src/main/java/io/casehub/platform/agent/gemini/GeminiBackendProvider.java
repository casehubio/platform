package io.casehub.platform.agent.gemini;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.BackendProvider;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** ServiceLoader-discovered factory for Gemini SDK backends. */
public class GeminiBackendProvider implements BackendProvider {

    @Override
    public String vendorKey() {
        return "gemini";
    }

    @Override
    public AgentBackend create(Map<String, String> properties) {
        return new GeminiAgentBackend(new GeminiAgentProperties() {
            @Override public Optional<String> apiKey() {
                return Optional.ofNullable(properties.get("api-key"));
            }
            @Override public String defaultModel() {
                return properties.getOrDefault("default-model", "gemini-2.0-flash");
            }
            @Override public Duration cacheTtl() {
                return durationProperty(properties, "cache-ttl", 300);
            }
            @Override public Duration defaultTimeout() {
                return durationProperty(properties, "default-timeout", 120);
            }
            @Override public int maxConcurrentSessions() {
                return intProperty(properties, "max-concurrent-sessions", 4);
            }
        });
    }
}
