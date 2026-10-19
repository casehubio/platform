package io.casehub.platform.agent.claude.spring;

import io.casehub.platform.agent.claude.AnthropicChatModelProperties;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "casehub.platform.agent.anthropic")
public record AnthropicChatModelSpringProperties(
        Optional<String> apiKey,
        @DefaultValue("claude-sonnet-4-20250514") String defaultModel,
        @DefaultValue("PT2M") Duration defaultTimeout,
        @DefaultValue("8192") int maxTokens) implements AnthropicChatModelProperties {
}
