package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;
import io.casehub.platform.api.process.ProcessCommand;
import io.casehub.platform.api.process.ProcessExecutionException;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.platform.api.process.ProcessResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessInvokeHandler implements InvokeHandler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper    objectMapper;
    private final ProcessExecutor processExecutor;

    public ProcessInvokeHandler(ObjectMapper objectMapper, ProcessExecutor processExecutor) {
        this.objectMapper    = objectMapper;
        this.processExecutor = processExecutor;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Process;
    }

    @Override
    public Action create(Declaration declaration, InvokeBinding binding) {
        InvokeBinding.Process proc = (InvokeBinding.Process) binding;
        return (params, services) -> executeProcess(proc, params);
    }

    @SuppressWarnings("unchecked")
    private Result executeProcess(InvokeBinding.Process proc, Map<String, Object> params) {
        List<String> command = new ArrayList<>();
        command.add(proc.command());
        for (String arg : proc.args()) {
            command.add(interpolate(arg, params));
        }

        try {
            ProcessCommand cmd = ProcessCommand.of(command.toArray(String[]::new));
            if (proc.workingDir() != null) {
                cmd = cmd.workingDir(proc.workingDir());
            }
            Duration timeout = io.casehub.yaml.core.orchestration.DurationParser.parseOrNull(proc.timeout());
            if (timeout != null) {
                cmd = cmd.timeout(timeout);
            }
            cmd = cmd.mergeStderr(true);

            long          start      = System.nanoTime();
            ProcessResult result     = processExecutor.execute(cmd);
            long          durationMs = (System.nanoTime() - start) / 1_000_000;

            Map<String, Object> metadata = Map.of("exitCode", result.exitCode(), "durationMs", durationMs);

            if (!result.isSuccess()) {
                String error = result.stdout() != null && !result.stdout().isBlank()
                               ? result.stdout().trim()
                               : "Process exited with code " + result.exitCode();
                return Result.failed(error);
            }

            String stdout = result.stdout() != null ? result.stdout() : "";

            Map<String, Object> output = switch (proc.output()) {
                case "json" -> objectMapper.readValue(stdout.trim(), LinkedHashMap.class);
                case "lines" -> Map.of("lines", Arrays.asList(stdout.trim().split("\n")));
                case "csv" -> Map.of("csv", stdout.trim());
                default -> Map.of("stdout", stdout);
            };

            return Result.of(output, metadata);
        } catch (ProcessExecutionException e) {
            return Result.failed("Process execution failed: " + e.getMessage());
        } catch (Exception e) {
            return Result.failed("Process execution failed: " + e.getMessage());
        }
    }

    private static String interpolate(String template, Map<String, Object> params) {
        Matcher       matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb      = new StringBuilder();
        while (matcher.find()) {
            String key   = matcher.group(1);
            Object value = params.get(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}
