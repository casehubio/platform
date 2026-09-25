package io.casehub.yaml.step.plugin;

import io.casehub.platform.api.process.DefaultProcessExecutor;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.platform.api.process.ProcessResult;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessPluginTest {

    @Test
    void executesCommandViaProcessExecutor() {
        ProcessExecutor executor = mock(ProcessExecutor.class);
        when(executor.execute(any(io.casehub.platform.api.process.ProcessCommand.class)))
                .thenReturn(new ProcessResult(0, "hello", "", false));

        var registry = new MapServiceRegistry().register(ProcessExecutor.class, executor);
        var action = loadAction();

        StepResult result = action.execute(
                Map.of("command", "/bin/echo", "args", List.of("hello")), registry);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("exitCode", 0);
        assertThat(result.output()).containsEntry("stdout", "hello");
    }

    @Test
    void returnsFailureOnNonZeroExitCode() {
        ProcessExecutor executor = mock(ProcessExecutor.class);
        when(executor.execute(any(io.casehub.platform.api.process.ProcessCommand.class)))
                .thenReturn(new ProcessResult(1, "", "error occurred", false));

        var registry = new MapServiceRegistry().register(ProcessExecutor.class, executor);
        var action = loadAction();

        StepResult result = action.execute(Map.of("command", "/bin/false"), registry);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void commandWithRealExecutor() {
        ProcessExecutor executor = new DefaultProcessExecutor();
        var registry = new MapServiceRegistry().register(ProcessExecutor.class, executor);
        var action = loadAction();

        StepResult result = action.execute(
                Map.of("command", "/bin/echo", "args", List.of("test")), registry);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("stdout").toString()).contains("test");
    }

    private StepAction loadAction() {
        try {
            Class<?> clazz = Class.forName(
                    "io.casehub.yaml.step.plugin.ProcessPluginAction");
            return (StepAction) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new AssertionError("APT-generated ProcessPluginAction not found: " + e, e);
        }
    }
}
