package io.casehub.yaml.step.handler;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpInvokeHandlerTest {

    @Test
    void supportsOnlyMcpBindings() {
        var handler = new McpInvokeHandler();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Python("test.py"))).isFalse();
    }

    @Test
    void createsStepActionFromBinding() {
        var handler = new McpInvokeHandler();
        var def = new StepDefinition("test", null,
                Map.of("input", new StepParameter(StepParameterType.STRING, true, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("test.tool"));

        StepAction action = handler.create(def, new InvokeBinding.Mcp("test.tool"));
        assertThat(action).isNotNull();

        StepResult result = action.execute(Map.of("input", "hello"), null);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.executionMetadata()).containsKey("tool");
    }
}
