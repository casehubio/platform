package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void supportsOnlyAgentBindings() {
        var handler = new AgentInvokeHandler(mapper);
        assertThat(handler.supports(new InvokeBinding.Agent("test", null, null, false))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void failsWithoutServiceRegistry() {
        var handler = new AgentInvokeHandler(mapper);
        var binding = new InvokeBinding.Agent("analyst", "claude-sonnet-5", "30s", false);
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        var action = handler.create(def, binding);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((StepResult.Failure) result).message()).contains("ServiceRegistry");
    }
}
