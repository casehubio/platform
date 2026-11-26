package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.CachePolicy;
import java.time.Duration;
import java.util.Optional;

public interface AnthropicChatModelProperties {

    Optional<String> apiKey();

    String defaultModel();

    Duration defaultTimeout();

    int maxTokens();

    default boolean cacheAutomatically() {
        return true;
    }

    default CachePolicy cachePolicy() {
        return cacheAutomatically() ? CachePolicy.SYSTEM_AND_TOOLS : CachePolicy.NONE;
    }
}
