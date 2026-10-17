package io.casehub.platform.agent.openai;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.BackendProvider;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** ServiceLoader-discovered factory for OpenAI backends. */
public class OpenAiBackendProvider implements BackendProvider {

    @Override
    public String vendorKey() {
        return "openai";
    }

    @Override
    public AgentBackend create(Map<String, String> properties) {
        return new OpenAiAgentBackend(new OpenAiAgentProperties() {
            @Override public Optional<String> apiKey() {
                return Optional.ofNullable(properties.get("api-key"));
            }
            @Override public String defaultModel() {
                return properties.getOrDefault("default-model", "gpt-4o");
            }
            @Override public String promptCacheRetention() {
                return properties.getOrDefault("prompt-cache-retention", "auto");
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
