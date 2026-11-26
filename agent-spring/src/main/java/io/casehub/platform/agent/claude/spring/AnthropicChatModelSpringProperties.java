package io.casehub.platform.agent.claude.spring;

import io.casehub.platform.agent.CachePolicy;
import io.casehub.platform.agent.claude.AnthropicChatModelProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties(prefix = "casehub.platform.agent.anthropic")
public record AnthropicChatModelSpringProperties(
        Optional<String> apiKey,
        @DefaultValue("claude-sonnet-4-20250514") String defaultModel,
        @DefaultValue("PT2M") Duration defaultTimeout,
        @DefaultValue("8192") int maxTokens,
        @DefaultValue("SYSTEM_AND_TOOLS") CachePolicy cachePolicy) implements AnthropicChatModelProperties {

    @Override
    public boolean cacheAutomatically() {
        return cachePolicy != CachePolicy.NONE;
    }
}
