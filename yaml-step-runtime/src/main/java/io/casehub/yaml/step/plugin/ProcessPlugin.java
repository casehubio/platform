package io.casehub.yaml.step.plugin;

import io.casehub.platform.api.process.ProcessCommand;
import io.casehub.platform.api.process.ProcessExecutionException;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.platform.api.process.ProcessResult;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.StepPlugin;
import io.casehub.yaml.plugin.api.StepResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@StepPlugin(value = "process", description = "Executes a system process via ProcessExecutor")
public record ProcessPlugin(
        @Required String command,
        @Optional List<String> args,
        @Optional String workingDir,
        @Optional String timeout,
        @Optional Boolean mergeStderr) {

    public ProcessPlugin {
        if (args == null) args = List.of();
        if (mergeStderr == null) mergeStderr = false;
    }

    @Execute
    public StepResult run(ProcessExecutor executor) {
        try {
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);

            ProcessCommand cmd = ProcessCommand.of(fullCommand.toArray(String[]::new));
            if (workingDir != null) {
                cmd = cmd.workingDir(workingDir);
            }
            if (timeout != null) {
                cmd = cmd.timeout(parseTimeout(timeout));
            }
            cmd = cmd.mergeStderr(mergeStderr);

            long start = System.nanoTime();
            ProcessResult result = executor.execute(cmd);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("exitCode", result.exitCode());
            output.put("stdout", result.stdout() != null ? result.stdout() : "");
            output.put("stderr", result.stderr() != null ? result.stderr() : "");
            output.put("timedOut", result.isTimedOut());

            if (!result.isSuccess()) {
                String error = result.stderr() != null && !result.stderr().isBlank()
                        ? result.stderr().trim()
                        : "Process exited with code " + result.exitCode();
                return StepResult.failed(error);
            }

            return StepResult.of(output, Map.of("durationMs", durationMs));
        } catch (ProcessExecutionException e) {
            return StepResult.failed("Process execution failed: " + e.getMessage());
        }
    }

    private static Duration parseTimeout(String timeout) {
        if (timeout.endsWith("ms")) return Duration.ofMillis(Long.parseLong(timeout.replace("ms", "")));
        if (timeout.endsWith("s")) return Duration.ofSeconds(Long.parseLong(timeout.replace("s", "")));
        if (timeout.endsWith("m")) return Duration.ofMinutes(Long.parseLong(timeout.replace("m", "")));
        return Duration.ofMillis(Long.parseLong(timeout));
    }
}
