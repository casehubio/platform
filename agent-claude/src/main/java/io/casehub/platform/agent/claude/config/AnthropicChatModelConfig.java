package io.casehub.platform.agent.claude.config;

import io.casehub.platform.agent.claude.AnthropicChatModelProperties;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.platform.agent.anthropic")
public interface AnthropicChatModelConfig extends AnthropicChatModelProperties {

    @Override
    Optional<String> apiKey();

    @Override
    @WithDefault("claude-sonnet-4-20250514")
    String defaultModel();

    @Override
    @WithDefault("PT2M")
    Duration defaultTimeout();

    @Override
    @WithDefault("8192")
    int maxTokens();
}
