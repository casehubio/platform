package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.process.CommandNotAllowedException;
import io.casehub.platform.api.process.ProcessCommand;
import io.casehub.platform.api.process.ProcessExecutionException;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.platform.api.process.ProcessResult;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.InvokeHandler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class PythonInvokeHandler implements InvokeHandler {

    private final ObjectMapper objectMapper;
    private final ProcessExecutor processExecutor;

    public PythonInvokeHandler(ObjectMapper objectMapper, ProcessExecutor processExecutor) {
        this.objectMapper = objectMapper;
        this.processExecutor = processExecutor;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Python;
    }

    @Override
    public StepAction create(StepDefinition definition, InvokeBinding binding) {
        InvokeBinding.Python python = (InvokeBinding.Python) binding;
        return (params, services) -> executePython(python, params);
    }

    @SuppressWarnings("unchecked")
    private StepResult executePython(InvokeBinding.Python python, Map<String, Object> params) {
        try {
            byte[] jsonInput = objectMapper.writeValueAsBytes(params);

            ProcessCommand cmd = ProcessCommand.of("python3", python.script())
                    .stdin(jsonInput)
                    .mergeStderr(false);

            Duration timeout = parseTimeout(python.timeout());
            if (timeout != null) {
                cmd = cmd.timeout(timeout);
            }

            long start = System.nanoTime();
            ProcessResult result = processExecutor.execute(cmd);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            if (!result.isSuccess()) {
                String error = result.stderr() != null && !result.stderr().isBlank()
                               ? result.stderr().trim()
                               : "Python script exited with code " + result.exitCode();
                return StepResult.failed(error);
            }

            String stdout = result.stdout() != null ? result.stdout().trim() : "";
            Map<String, Object> output = objectMapper.readValue(stdout, LinkedHashMap.class);
            return StepResult.of(output, Map.of("durationMs", durationMs, "script", python.script()));

        } catch (CommandNotAllowedException e) {
            return StepResult.failed("Python script not allowed: " + e.getMessage());
        } catch (ProcessExecutionException e) {
            return StepResult.failed("Python execution failed: " + e.getMessage());
        } catch (Exception e) {
            return StepResult.failed("Python execution failed: " + e.getMessage());
        }
    }

    private static Duration parseTimeout(String timeout) {
        if (timeout == null) return null;
        if (timeout.endsWith("ms")) return Duration.ofMillis(Long.parseLong(timeout.replace("ms", "")));
        if (timeout.endsWith("s")) return Duration.ofSeconds(Long.parseLong(timeout.replace("s", "")));
        if (timeout.endsWith("m")) return Duration.ofMinutes(Long.parseLong(timeout.replace("m", "")));
        return null;
    }
}
