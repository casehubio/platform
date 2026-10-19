package io.casehub.platform.agent.claude;

import java.time.Duration;
import java.util.Optional;

public interface AnthropicChatModelProperties {

    Optional<String> apiKey();

    String defaultModel();

    Duration defaultTimeout();

    int maxTokens();
}
