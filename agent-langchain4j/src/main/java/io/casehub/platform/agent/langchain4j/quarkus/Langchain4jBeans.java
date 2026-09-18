package io.casehub.platform.agent.langchain4j.quarkus;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.langchain4j.AgentProviderChatModel;
import io.casehub.platform.agent.langchain4j.ChatModelAgentProvider;
import io.casehub.platform.agent.langchain4j.config.AgentLangchain4jConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class Langchain4jBeans {

    @Produces
    @ApplicationScoped
    public ChatModelAgentProvider chatModelAgentProvider(
            @Any Instance<ChatModel> chatModels,
            AgentLangchain4jConfig config) {
        List<ChatModel> allModels = chatModels.select(Default.Literal.INSTANCE).stream().toList();
        return ChatModelAgentProvider.create(allModels, config);
    }

    @Produces
    @DefaultBean
    @Priority(10)
    @ApplicationScoped
    public AgentProviderChatModel agentProviderChatModel(
            AgentProvider agentProvider,
            @Any Instance<ChatModelListener> listeners,
            AgentLangchain4jConfig config) {
        return new AgentProviderChatModel(agentProvider, listeners.stream().toList(), config);
    }
}
