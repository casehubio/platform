package io.casehub.platform.agent.claude;

import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.TextBlockParam;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicCachingTest {

    @Test
    void extractSystemBlocks_withCaching_addsCacheControl() {
        List<ChatMessage> messages = List.of(
            SystemMessage.from("You are helpful"),
            UserMessage.from("Hello")
        );

        var blocks = LangChain4jAnthropicMapper.extractSystemBlocks(messages, true);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).cacheControl())
            .isPresent()
            .get()
            .isInstanceOf(CacheControlEphemeral.class);
    }

    @Test
    void extractSystemBlocks_withoutCaching_noCacheControl() {
        List<ChatMessage> messages = List.of(
            SystemMessage.from("You are helpful"),
            UserMessage.from("Hello")
        );

        var blocks = LangChain4jAnthropicMapper.extractSystemBlocks(messages, false);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).cacheControl()).isEmpty();
    }

    @Test
    void extractSystemBlocks_defaultOverload_enablesCaching() {
        List<ChatMessage> messages = List.of(
            SystemMessage.from("Be concise"),
            UserMessage.from("Hi")
        );

        var blocks = LangChain4jAnthropicMapper.extractSystemBlocks(messages);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).cacheControl()).isPresent();
    }
}
