package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void supportsOnlyProcessBindings() {
        var handler = new ProcessInvokeHandler(mapper);
        assertThat(handler.supports(new InvokeBinding.Process("/bin/echo", null, null, null, null, null, null))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void executesCommandWithJsonOutput() {
        var handler = new ProcessInvokeHandler(mapper);
        var binding = new InvokeBinding.Process("/bin/echo", List.of("{\"key\":\"value\"}"),
                "json", "5s", null, null, null);
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        StepAction action = handler.create(def, binding);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("key", "value");
        assertThat(result.executionMetadata()).containsKey("exitCode");
        assertThat(result.executionMetadata()).containsKey("durationMs");
    }

    @Test
    void executesCommandWithRawOutput() {
        var handler = new ProcessInvokeHandler(mapper);
        var binding = new InvokeBinding.Process("/bin/echo", List.of("hello world"),
                "raw", null, null, null, null);
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        StepAction action = handler.create(def, binding);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsKey("stdout");
    }

    @Test
    void executesCommandWithLinesOutput() {
        var handler = new ProcessInvokeHandler(mapper);
        var binding = new InvokeBinding.Process("/bin/echo", List.of("line1"),
                "lines", null, null, null, null);
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        StepAction action = handler.create(def, binding);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsKey("lines");
    }

    @Test
    void failsOnNonZeroExitCode() {
        var handler = new ProcessInvokeHandler(mapper);
        var binding = new InvokeBinding.Process("/bin/sh", List.of("-c", "exit 1"),
                "raw", null, null, null, "stderr");
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        StepAction action = handler.create(def, binding);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void interpolatesVariablesInArgs() {
        var handler = new ProcessInvokeHandler(mapper);
        var binding = new InvokeBinding.Process("/bin/echo", List.of("${greeting}"),
                "raw", null, null, null, null);
        var def = new StepDefinition("test", null, Map.of(), Map.of(), binding);

        StepAction action = handler.create(def, binding);
        StepResult result = action.execute(Map.of("greeting", "hello"), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((String) result.output().get("stdout")).trim()).isEqualTo("hello");
    }
}
