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
    void pythonBindingStoresScript() {
        var binding = new InvokeBinding.Python("steps/sentiment.py");
        assertThat(binding.script()).isEqualTo("steps/sentiment.py");
    }

    @Test
    void sealedInterfacePermitsSixTypes() {
        assertThat(InvokeBinding.class.getPermittedSubclasses()).hasSize(6);
    }
}
