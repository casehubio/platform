package io.casehub.platform.agent.router.spring;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.RoutingChatModel;
import io.casehub.platform.api.model.ModelRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RoutingChatModel.class)
public class ChatModelRouterManualConfig {

    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel routingChatModel(
            List<ChatModelBackend> backends,
            ModelRegistry modelRegistry,
            @Value("${casehub.platform.agent.default-backend:claude}") String defaultBackend) {
        return new RoutingChatModel(backends, modelRegistry, defaultBackend, null);
    }
}
