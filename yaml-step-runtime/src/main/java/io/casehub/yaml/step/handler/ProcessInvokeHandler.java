package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.InvokeHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessInvokeHandler implements InvokeHandler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper objectMapper;

    public ProcessInvokeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Process;
    }

    @Override
    public StepAction create(StepDefinition definition, InvokeBinding binding) {
        InvokeBinding.Process proc = (InvokeBinding.Process) binding;
        return (params, services) -> executeProcess(proc, params);
    }

    @SuppressWarnings("unchecked")
    private StepResult executeProcess(InvokeBinding.Process proc, Map<String, Object> params) {
        List<String> command = new ArrayList<>();
        command.add(proc.command());
        for (String arg : proc.args()) {
            command.add(interpolate(arg, params));
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (!proc.env().isEmpty()) {
                pb.environment().putAll(proc.env());
            }
            if (proc.workingDir() != null) {
                pb.directory(new java.io.File(proc.workingDir()));
            }

            long start = System.nanoTime();
            Process process = pb.start();

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            long timeoutMs = parseTimeout(proc.timeout());
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return StepResult.failed("Process timed out after " + timeoutMs + "ms");
            }

            int exitCode = process.exitValue();
            Map<String, Object> metadata = Map.of("exitCode", exitCode, "durationMs", durationMs);

            if (exitCode != 0) {
                String error = stderr.isEmpty() ? "Process exited with code " + exitCode : stderr.trim();
                return StepResult.failed(error);
            }

            Map<String, Object> output = switch (proc.output()) {
                case "json" -> objectMapper.readValue(stdout.trim(), LinkedHashMap.class);
                case "lines" -> Map.of("lines", Arrays.asList(stdout.trim().split("\n")));
                case "csv" -> Map.of("csv", stdout.trim());
                default -> Map.of("stdout", stdout);
            };

            return StepResult.of(output, metadata);
        } catch (IOException e) {
            return StepResult.failed("Process execution failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failed("Process interrupted");
        }
    }

    private static String interpolate(String template, Map<String, Object> params) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params.get(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static long parseTimeout(String timeout) {
        if (timeout == null) return 30_000;
        if (timeout.endsWith("ms")) return Long.parseLong(timeout.replace("ms", ""));
        if (timeout.endsWith("s")) return Long.parseLong(timeout.replace("s", "")) * 1000;
        if (timeout.endsWith("m")) return Long.parseLong(timeout.replace("m", "")) * 60_000;
        return 30_000;
    }
}
