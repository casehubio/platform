package io.casehub.platform.agent.openai.quarkus;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.platform.agent.openai.OpenAiAgentBackend;
import io.casehub.platform.agent.openai.OpenAiNativeChatModel;
import io.casehub.platform.agent.openai.config.OpenAiAgentConfig;
import io.casehub.platform.agent.router.ChatModelBackend;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class OpenAiBeans {

    private OpenAiAgentBackend backend;

    @Produces
    @ApplicationScoped
    public OpenAiAgentBackend openAiAgentBackend(OpenAiAgentConfig config) {
        backend = new OpenAiAgentBackend(config);
        return backend;
    }

    @Produces
    @ApplicationScoped
    public ChatModelBackend openAiChatModelBackend(OpenAiAgentConfig config) {
        var clientBuilder = com.openai.client.okhttp.OpenAIOkHttpClient.builder();
        config.apiKey().ifPresent(clientBuilder::apiKey);
        OpenAiNativeChatModel chatModel = new OpenAiNativeChatModel(
                clientBuilder.build(), config.defaultModel(), config.defaultTimeout());
        return new ChatModelBackend() {
            @Override
            public String key()                                     {return "openai";}

            @Override
            public ChatModel chatModel() {return chatModel;}
        };
    }


    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
