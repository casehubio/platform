package io.casehub.yaml.step.handler;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpInvokeHandlerTest {

    @Test
    void supportsOnlyMcpBindings() {
        var handler = new McpInvokeHandler();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Script("python3", "test.py", null, null, null))).isFalse();
    }

    @Test
    void createsStepActionFromBinding() {
        var handler = new McpInvokeHandler();
        var def = new Declaration("test", null,
                Map.of("input", new Parameter(ParameterType.STRING, true, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("test.tool"));

        Action action = handler.create(def, new InvokeBinding.Mcp("test.tool"));
        assertThat(action).isNotNull();

        Result result = action.execute(Map.of("input", "hello"), null);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.executionMetadata()).containsKey("tool");
    }
}
