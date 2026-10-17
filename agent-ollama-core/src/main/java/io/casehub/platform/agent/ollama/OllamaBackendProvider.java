package io.casehub.platform.agent.ollama;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.BackendProvider;

import java.time.Duration;
import java.util.Map;

/** ServiceLoader-discovered factory for Ollama backends. */
public class OllamaBackendProvider implements BackendProvider {

    @Override
    public String vendorKey() {
        return "ollama";
    }

    @Override
    public AgentBackend create(Map<String, String> properties) {
        return new OllamaAgentBackend(new OllamaAgentProperties() {
            @Override public String host() {
                return properties.getOrDefault("host", "http://localhost:11434");
            }
            @Override public String defaultModel() {
                return properties.getOrDefault("default-model", "llama3.2");
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
