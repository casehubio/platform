package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.InvokeHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PythonInvokeHandler implements InvokeHandler {

    private final ObjectMapper objectMapper;

    public PythonInvokeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            ProcessBuilder pb = new ProcessBuilder("python3", python.script());
            pb.redirectErrorStream(false);
            long start = System.nanoTime();
            Process process = pb.start();

            try (OutputStream stdin = process.getOutputStream()) {
                objectMapper.writeValue(stdin, params);
            }

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return StepResult.failed("Python script timed out");
            }

            if (process.exitValue() != 0) {
                return StepResult.failed("Python script failed: " + stderr.trim());
            }

            Map<String, Object> output = objectMapper.readValue(stdout.trim(), LinkedHashMap.class);
            return StepResult.of(output, Map.of("durationMs", durationMs, "script", python.script()));
        } catch (IOException e) {
            return StepResult.failed("Python execution failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failed("Python execution interrupted");
        }
    }
}
