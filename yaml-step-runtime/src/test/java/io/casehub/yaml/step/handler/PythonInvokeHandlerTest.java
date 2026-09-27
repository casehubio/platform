package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.process.CommandNotAllowedException;
import io.casehub.platform.api.process.CommandPattern;
import io.casehub.platform.api.process.DefaultProcessExecutor;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PythonInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProcessExecutor executor = new DefaultProcessExecutor();

    @Test
    void supportsOnlyPythonBindings() {
        var handler = new PythonInvokeHandler(mapper, executor);
        assertThat(handler.supports(new InvokeBinding.Python("script.py"))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void createsNonNullAction() {
        var handler = new PythonInvokeHandler(mapper, executor);
        var binding = new InvokeBinding.Python("steps/sentiment.py");
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);
        assertThat(handler.create(def, binding)).isNotNull();
    }

    @Test
    void failsGracefullyOnMissingScript() {
        var handler = new PythonInvokeHandler(mapper, executor);
        var binding = new InvokeBinding.Python("nonexistent-script.py", "5s");
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        StepAction action = handler.create(def, binding);
        StepResult result = action.execute(Map.of("key", "value"), null);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void respectsAllowList() {
        var restricted = new DefaultProcessExecutor(List.of(
                new CommandPattern(List.of("echo"), CommandPattern.MatchMode.PREFIX)));
        var handler = new PythonInvokeHandler(mapper, restricted);
        var binding = new InvokeBinding.Python("script.py");
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        StepAction action = handler.create(def, binding);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((StepResult.Failure) result).message()).contains("not allowed");
    }

    @Test
    void usesConfiguredTimeout() {
        var binding = new InvokeBinding.Python("script.py", "60s");
        assertThat(binding.timeout()).isEqualTo("60s");
    }
}
