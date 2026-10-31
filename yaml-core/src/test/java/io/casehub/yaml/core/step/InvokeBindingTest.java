package io.casehub.yaml.core.step;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InvokeBindingTest {

    @Test
    void mcpBindingStoresToolName() {
        var binding = new InvokeBinding.Mcp("fsi.risk.assess");
        assertThat(binding.tool()).isEqualTo("fsi.risk.assess");
    }

    @Test
    void restBindingDefaultsMethodToGet() {
        var binding = new InvokeBinding.Rest(null, "/api/test", null, null);
        assertThat(binding.method()).isEqualTo("GET");
        assertThat(binding.headers()).isEmpty();
        assertThat(binding.body()).isEmpty();
    }

    @Test
    void restBindingPreservesAllFields() {
        var binding = new InvokeBinding.Rest("POST", "/api/notifications",
                Map.of("Content-Type", "application/json"),
                Map.of("message", "${message}"));
        assertThat(binding.method()).isEqualTo("POST");
        assertThat(binding.url()).isEqualTo("/api/notifications");
        assertThat(binding.headers()).containsEntry("Content-Type", "application/json");
        assertThat(binding.body()).containsEntry("message", "${message}");
    }

    @Test
    void agentBindingRequiresDescriptor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.Agent(null, null, null, false))
                .withMessageContaining("Agent binding requires descriptor");
    }

    @Test
    void agentBindingAcceptsOptionalModelAndTimeout() {
        var binding = new InvokeBinding.Agent("analyst", "claude-sonnet-5", "30s", true);
        assertThat(binding.descriptor()).isEqualTo("analyst");
        assertThat(binding.model()).isEqualTo("claude-sonnet-5");
        assertThat(binding.timeout()).isEqualTo("30s");
        assertThat(binding.structuredOutput()).isTrue();
    }

    @Test
    void agentBindingWithPoolAndResponseFormat() {
        var agent = new InvokeBinding.Agent("summarizer", "claude-sonnet-5",
                                            null, "You are...", "Summarize", "30s", false,
                                            "my-pool", null, Map.of("type", "json", "name", "summary"));
        assertThat(agent.pool()).isEqualTo("my-pool");
        assertThat(((Map<?, ?>) agent.responseFormat()).get("type")).isEqualTo("json");
    }

    @Test
    void agentBindingWithModelChain() {
        var agent = new InvokeBinding.Agent("classifier", null,
                                            null, "You classify", "Classify this", "30s", false,
                                            null, List.of("claude-opus-4", "claude-sonnet-5"), null);
        assertThat(agent.modelChain()).containsExactly("claude-opus-4", "claude-sonnet-5");
    }

    @Test
    void agentBindingBackwardCompatConstructor() {
        var agent = new InvokeBinding.Agent("analyst", "claude-sonnet-5", "30s", true);
        assertThat(agent.pool()).isNull();
        assertThat(agent.modelChain()).isNull();
        assertThat(agent.responseFormat()).isNull();
    }

    @Test
    void agentBindingFullConstructorBackwardCompat() {
        var agent = new InvokeBinding.Agent("desc", "model", null, "sys", "usr", "10s", false);
        assertThat(agent.pool()).isNull();
        assertThat(agent.modelChain()).isNull();
        assertThat(agent.responseFormat()).isNull();
    }


    @Test
    void processBindingRequiresCommand() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.Process(null, null, null, null, null, null, null))
                .withMessageContaining("Process binding requires command");
    }

    @Test
    void processBindingDefaultsOutputToJson() {
        var binding = new InvokeBinding.Process("/bin/echo", List.of("hello"), null, null, null, null, null);
        assertThat(binding.output()).isEqualTo("json");
        assertThat(binding.args()).containsExactly("hello");
        assertThat(binding.env()).isEmpty();
        assertThat(binding.onError()).isEqualTo("stderr");
    }

    @Test
    void graphqlBindingStoresQuery() {
        var binding = new InvokeBinding.Graphql("{ positions { symbol quantity } }");
        assertThat(binding.query()).contains("positions");
    }

    @Test
    void scriptBindingStoresFields() {
        var binding = new InvokeBinding.Script("python3", "steps/sentiment.py", "60s", "/opt", null);
        assertThat(binding.runtime()).isEqualTo("python3");
        assertThat(binding.script()).isEqualTo("steps/sentiment.py");
        assertThat(binding.timeout()).isEqualTo("60s");
        assertThat(binding.workingDir()).isEqualTo("/opt");
        assertThat(binding.env()).isEmpty();
    }

    @Test
    void sealedInterfacePermitsAllTypes() {
        assertThat(InvokeBinding.class.getPermittedSubclasses()).hasSize(11);
    }

    @Test
    void scriptBindingDefaults() {
        var binding = new InvokeBinding.Script("python3", "script.py", null, null, null);
        assertThat(binding.timeout()).isEqualTo("30s");
        assertThat(binding.env()).isEmpty();
        assertThat(binding.workingDir()).isNull();
    }

    @Test
    void scriptBindingConstants() {
        assertThat(InvokeBinding.Script.PYTHON).isEqualTo("python3");
        assertThat(InvokeBinding.Script.NODE).isEqualTo("node");
    }

    @Test
    void scriptBindingRequiresRuntime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.Script(null, "script.py", null, null, null))
                .withMessageContaining("requires runtime");
    }

    @Test
    void scriptBindingRequiresScript() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.Script("python3", null, null, null, null))
                .withMessageContaining("requires script");
    }

    @Test
    void ariaBindingStoresAction() {
        var binding = new InvokeBinding.Aria("click");
        assertThat(binding.action()).isEqualTo("click");
    }

    @Test
    void ariaBindingRequiresAction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.Aria(null))
                .withMessageContaining("requires action");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.Aria(""))
                .withMessageContaining("requires action");
    }

    @Test
    void sessionOpen_allFields() {
        var open = new InvokeBinding.SessionOpen(
                "analyst", "You are a data analyst", "claude-opus-4",
                null, null, "analysis-pool", "60s", "10s");
        assertThat(open.session()).isEqualTo("analyst");
        assertThat(open.system()).isEqualTo("You are a data analyst");
        assertThat(open.model()).isEqualTo("claude-opus-4");
        assertThat(open.pool()).isEqualTo("analysis-pool");
        assertThat(open.timeout()).isEqualTo("60s");
        assertThat(open.checkoutTimeout()).isEqualTo("10s");
    }

    @Test
    void sessionOpen_requiresSession() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.SessionOpen(
                        null, "system", null, null, null, null, null, null))
                .withMessageContaining("session");
    }

    @Test
    void sessionQuery_allFields() {
        var query = new InvokeBinding.SessionQuery(
                "analyst", "Summarize this", Map.of("type", "json"), "result", true);
        assertThat(query.session()).isEqualTo("analyst");
        assertThat(query.user()).isEqualTo("Summarize this");
        assertThat(query.priority()).isTrue();
        assertThat(query.output()).isEqualTo("result");
    }

    @Test
    void sessionQuery_requiresSessionAndUser() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.SessionQuery(
                        null, "prompt", null, null, false))
                .withMessageContaining("session");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.SessionQuery(
                        "sess", null, null, null, false))
                .withMessageContaining("user");
    }

    @Test
    void sessionClear_requiresSession() {
        var clear = new InvokeBinding.SessionClear("analyst");
        assertThat(clear.session()).isEqualTo("analyst");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.SessionClear(null))
                .withMessageContaining("session");
    }

    @Test
    void sessionClose_requiresSession() {
        var close = new InvokeBinding.SessionClose("analyst");
        assertThat(close.session()).isEqualTo("analyst");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvokeBinding.SessionClose(null))
                .withMessageContaining("session");
    }

}
