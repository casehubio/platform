package io.casehub.platform.agent.claude;

import com.anthropic.models.messages.CacheControlEphemeral;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.casehub.platform.agent.CachePolicy;
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

    @Test
    void toAnthropicTools_withNonePolicy_noCacheControl() {
        var tools = List.of(
                ToolSpecification.builder().name("tool1").description("desc").build(),
                ToolSpecification.builder().name("tool2").description("desc").build()
                           );

        var result = LangChain4jAnthropicMapper.toAnthropicTools(tools, CachePolicy.NONE);

        assertThat(result).hasSize(2);
        assertThat(result.get(result.size() - 1).cacheControl()).isEmpty();
    }

    @Test
    void toAnthropicTools_withSystemAndToolsPolicy_cacheControlOnLastTool() {
        var tools = List.of(
                ToolSpecification.builder().name("tool1").description("desc").build(),
                ToolSpecification.builder().name("tool2").description("desc").build()
                           );

        var result = LangChain4jAnthropicMapper.toAnthropicTools(tools, CachePolicy.SYSTEM_AND_TOOLS);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).cacheControl()).isEmpty();
        assertThat(result.get(1).cacheControl())
                .isPresent()
                .get()
                .isInstanceOf(CacheControlEphemeral.class);
    }

    @Test
    void extractSystemBlocks_withNonePolicy_noCacheControl() {
        var messages = List.<ChatMessage>of(SystemMessage.from("system"), UserMessage.from("hi"));

        var blocks = LangChain4jAnthropicMapper.extractSystemBlocks(messages, CachePolicy.NONE);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).cacheControl()).isEmpty();
    }

    @Test
    void extractSystemBlocks_withSystemAndToolsPolicy_addsCacheControl() {
        var messages = List.<ChatMessage>of(SystemMessage.from("system"), UserMessage.from("hi"));

        var blocks = LangChain4jAnthropicMapper.extractSystemBlocks(messages, CachePolicy.SYSTEM_AND_TOOLS);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).cacheControl())
                .isPresent()
                .get()
                .isInstanceOf(CacheControlEphemeral.class);
    }
}
