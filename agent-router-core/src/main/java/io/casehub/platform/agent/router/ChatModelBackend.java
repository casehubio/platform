package io.casehub.platform.agent.router;

import dev.langchain4j.model.chat.ChatModel;

public interface ChatModelBackend {

    String key();

    ChatModel chatModel();
}
