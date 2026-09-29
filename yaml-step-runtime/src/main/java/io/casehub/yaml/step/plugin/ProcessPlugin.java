package io.casehub.yaml.step.plugin;

import io.casehub.platform.api.process.ProcessCommand;
import io.casehub.platform.api.process.ProcessExecutionException;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.platform.api.process.ProcessResult;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Plugin(value = "process", description = "Executes a system process via ProcessExecutor")
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
    public Result run(ProcessExecutor executor) {
        try {
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);

            ProcessCommand cmd = ProcessCommand.of(fullCommand.toArray(String[]::new));
            if (workingDir != null) {
                cmd = cmd.workingDir(workingDir);
            }
            if (timeout != null) {
                cmd = cmd.timeout(io.casehub.yaml.core.orchestration.DurationParser.parseOrNull(timeout));
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
                return Result.failed(error);
            }

            return Result.of(output, Map.of("durationMs", durationMs));
        } catch (ProcessExecutionException e) {
            return Result.failed("Process execution failed: " + e.getMessage());
        }
    }

}
