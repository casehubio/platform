package io.casehub.platform.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AgentSessionConfigTest {

    @Test
    void of_minimalFactory_hasExpectedValues() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.systemPrompt()).isEqualTo("sys");
        assertThat(config.userPrompt()).isEqualTo("user");
        assertThat(config.mcpServers()).isEmpty();
        assertThat(config.timeout()).isNull();
        assertThat(config.correlationId()).isNull();
    }

    @Test
    void of_withTimeout_setsTimeout() {
        var timeout = Duration.ofSeconds(30);
        var config = AgentSessionConfig.of("sys", "user", timeout);
        assertThat(config.timeout()).isEqualTo(timeout);
    }

    @Test
    void compactConstructor_nullSystemPrompt_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AgentSessionConfig(null, "user", List.of(), null, null, null))
            .withMessageContaining("systemPrompt");
    }

    @Test
    void compactConstructor_nullUserPrompt_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AgentSessionConfig("sys", null, List.of(), null, null, null))
            .withMessageContaining("userPrompt");
    }

    @Test
    void compactConstructor_nullMcpServers_treatedAsEmpty() {
        var config = new AgentSessionConfig("sys", "user", null, null, null, null);
        assertThat(config.mcpServers()).isEmpty();
    }

    @Test
    void compactConstructor_mcpServers_defensiveCopy() {
        var servers = new ArrayList<AgentMcpServer>();
        servers.add(new AgentMcpServer.Stdio("cmd"));
        var config = new AgentSessionConfig("sys", "user", servers, null, null, null);
        servers.add(new AgentMcpServer.Sse("http://x"));
        assertThat(config.mcpServers()).hasSize(1);
    }

    @Test
    void modelDefaultsToNull() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.model()).isNull();
    }

    @Test
    void modelSetViaFactory() {
        var config = AgentSessionConfig.of("sys", "user", "claude");
        assertThat(config.model()).isEqualTo("claude");
    }

    @Test
    void responseFormatDefaultsToNull() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.responseFormat()).isNull();
    }

    @Test
    void withResponseFormatSetsFormat() {
        var config  = AgentSessionConfig.of("sys", "user");
        var format  = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        var updated = config.withResponseFormat(format);
        assertThat(updated.responseFormat()).isEqualTo(format);
        assertThat(updated.systemPrompt()).isEqualTo("sys");
        assertThat(updated.userPrompt()).isEqualTo("user");
    }

    @Test
    void withModelPreservesResponseFormat() {
        var format  = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        var config  = AgentSessionConfig.of("sys", "user").withResponseFormat(format);
        var updated = config.withModel("gpt-4");
        assertThat(updated.responseFormat()).isEqualTo(format);
        assertThat(updated.model()).isEqualTo("gpt-4");
    }

    @Test
    void modelParametersDefaultsToNull() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.modelParameters()).isNull();
    }

    @Test
    void withModelParametersSetsParameters() {
        var config  = AgentSessionConfig.of("sys", "user");
        var params  = ModelParameters.of().withTemperature(0.7).withMaxTokens(1024);
        var updated = config.withModelParameters(params);
        assertThat(updated.modelParameters()).isEqualTo(params);
        assertThat(updated.modelParameters().temperature()).isEqualTo(0.7);
        assertThat(updated.modelParameters().maxTokens()).isEqualTo(1024);
        assertThat(updated.systemPrompt()).isEqualTo("sys");
    }

    @Test
    void withModelPreservesModelParameters() {
        var params  = ModelParameters.of().withTemperature(0.5);
        var config  = AgentSessionConfig.of("sys", "user").withModelParameters(params);
        var updated = config.withModel("gpt-4");
        assertThat(updated.modelParameters()).isEqualTo(params);
        assertThat(updated.model()).isEqualTo("gpt-4");
    }

    @Test
    void toolsDefaultsToEmptyList() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.tools()).isEmpty();
    }

    @Test
    void toolsFieldCarriesSpecifications() {
        var tool   = ToolSpecification.builder().name("calculator").description("math").build();
        var config = AgentSessionConfig.of("sys", "user").withTools(List.of(tool));
        assertThat(config.tools()).hasSize(1);
        assertThat(config.tools().get(0).name()).isEqualTo("calculator");
    }

    @Test
    void withToolsPreservesOtherFields() {
        var format = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        var tool   = ToolSpecification.builder().name("calc").description("math").build();
        var config = AgentSessionConfig.of("sys", "user")
                                       .withResponseFormat(format)
                                       .withModel("gpt-4")
                                       .withTools(List.of(tool));
        assertThat(config.tools()).hasSize(1);
        assertThat(config.responseFormat()).isEqualTo(format);
        assertThat(config.model()).isEqualTo("gpt-4");
    }


    @Test
    void cachePolicyEnumValues() {
        assertThat(CachePolicy.values())
                .containsExactly(CachePolicy.NONE, CachePolicy.SYSTEM_AND_TOOLS, CachePolicy.ALL_STATIC);
    }

    @Test
    void cachePolicyDefaultsToNull() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.cachePolicy()).isNull();
    }

    @Test
    void withCachePolicySetsPolicy() {
        var config = AgentSessionConfig.of("sys", "user")
                                       .withCachePolicy(CachePolicy.SYSTEM_AND_TOOLS);
        assertThat(config.cachePolicy()).isEqualTo(CachePolicy.SYSTEM_AND_TOOLS);
    }

    @Test
    void withCachePolicyPreservesOtherFields() {
        var format = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        var config = AgentSessionConfig.of("sys", "user")
                                       .withResponseFormat(format)
                                       .withModel("gpt-4")
                                       .withCachePolicy(CachePolicy.ALL_STATIC);
        assertThat(config.cachePolicy()).isEqualTo(CachePolicy.ALL_STATIC);
        assertThat(config.responseFormat()).isEqualTo(format);
        assertThat(config.model()).isEqualTo("gpt-4");
    }
}
