package io.casehub.platform.agent.router;

import dev.langchain4j.model.chat.ChatModel;

public record SimpleChatModelBackend(String key, ChatModel chatModel) implements ChatModelBackend {
}
