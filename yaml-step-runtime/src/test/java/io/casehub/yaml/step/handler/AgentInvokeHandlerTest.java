package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static io.casehub.platform.agent.AgentProvider providerReturning(io.casehub.platform.agent.AgentEvent... events) {
        return new io.casehub.platform.agent.AgentProvider() {
            @Override
            public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return io.smallrye.mutiny.Multi.createFrom().items(events);
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException("not used in tests");
            }
        };
    }

    private static io.casehub.platform.agent.AgentProvider providerWithAssertions(
            java.util.function.Consumer<io.casehub.platform.agent.AgentSessionConfig> assertions,
            io.casehub.platform.agent.AgentEvent... events) {
        return new io.casehub.platform.agent.AgentProvider() {
            @Override
            public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                assertions.accept(config);
                return io.smallrye.mutiny.Multi.createFrom().items(events);
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException("not used in tests");
            }
        };
    }

    @Test
    void supportsOnlyAgentBindings() {
        var handler = new AgentInvokeHandler(mapper, null, java.util.List.of());
        assertThat(handler.supports(new InvokeBinding.Agent("test", null, null, false))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void invokesAgentProviderAndCollectsText() {
        var provider = providerReturning(
                new io.casehub.platform.agent.AgentEvent.TextDelta("Hello "),
                new io.casehub.platform.agent.AgentEvent.TextDelta("world"),
                new io.casehub.platform.agent.AgentEvent.InvocationComplete(100, 50, 0, 0, 0, 0.01, 500, 400, null, 1, false));

        var handler = new AgentInvokeHandler(mapper, provider, java.util.List.of());
        var binding = new InvokeBinding.Agent("analyst", null, "30s", false);
        var def     = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        var    action = handler.create(def, binding);
        Result result = action.execute(Map.of("input", "data"), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsKey("response");
        assertThat((String) result.output().get("response")).isEqualTo("Hello world");
        assertThat(result.executionMetadata()).containsEntry("descriptor", "analyst");
        assertThat(result.executionMetadata()).containsKey("inputTokens");
    }

    @Test
    void resolvesDescriptorFromRegistrar() {
        var provider = providerWithAssertions(
                config -> assertThat(config.systemPrompt()).contains("You are a trade analyst"),
                new io.casehub.platform.agent.AgentEvent.TextDelta("analysis done"),
                new io.casehub.platform.agent.AgentEvent.InvocationComplete(50, 30, 0, 0, 0, null, 200, 150, null, 1, false));

        io.casehub.eidos.api.AgentDescriptor descriptor = io.casehub.eidos.api.AgentDescriptor.builder()
                                                                                              .agentId("trade-analyst")
                                                                                              .name("Trade Analyst")
                                                                                              .slot("worker")
                                                                                              .tenancyId("test-tenant")
                                                                                              .briefing("You are a trade analyst")
                                                                                              .build();

        var handler = new AgentInvokeHandler(mapper, provider,
                                             java.util.List.of(() -> java.util.List.of(descriptor)));
        var binding = new InvokeBinding.Agent("trade-analyst", null, null, false);
        var def     = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        var    action = handler.create(def, binding);
        Result result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat((String) result.output().get("response")).isEqualTo("analysis done");
    }

    @Test
    void structuredOutputParsesJson() {
        var provider = providerReturning(
                new io.casehub.platform.agent.AgentEvent.TextDelta("{\"level\":\"HIGH\",\"score\":0.95}"),
                new io.casehub.platform.agent.AgentEvent.InvocationComplete(50, 30, 0, 0, 0, null, 200, 150, null, 1, false));

        var handler = new AgentInvokeHandler(mapper, provider, java.util.List.of());
        var binding = new InvokeBinding.Agent("analyst", null, null, true);
        var def     = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        var    action = handler.create(def, binding);
        Result result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("level", "HIGH");
        assertThat(result.output()).containsEntry("score", 0.95);
    }

    @Test
    void failsGracefullyWithoutProvider() {
        var handler = new AgentInvokeHandler(mapper, null, java.util.List.of());
        var binding = new InvokeBinding.Agent("analyst", null, null, false);
        var def     = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        var    action = handler.create(def, binding);
        Result result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("AgentProvider");
    }
}
