package io.casehub.yaml.core.step;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StepDefinitionParser {

    private StepDefinitionParser() {}

    @SuppressWarnings("unchecked")
    public static StepDefinitionFile parse(Map<String, Object> yaml) {
        String namespace = (String) yaml.getOrDefault("namespace", "");
        Map<String, Object> actions = (Map<String, Object>) yaml.get("actions");
        if (actions == null) {
            return new StepDefinitionFile(namespace, Map.of());
        }

        Map<String, StepDefinition> parsed = new LinkedHashMap<>();
        for (var entry : actions.entrySet()) {
            parsed.put(entry.getKey(),
                    parseAction(entry.getKey(), (Map<String, Object>) entry.getValue()));
        }
        return new StepDefinitionFile(namespace, parsed);
    }

    @SuppressWarnings("unchecked")
    static StepDefinition parseAction(String name, Map<String, Object> raw) {
        String description = (String) raw.get("description");

        Map<String, StepParameter> inputs = Map.of();
        if (raw.containsKey("inputs")) {
            inputs = parseParameters((Map<String, Object>) raw.get("inputs"));
        }

        Map<String, StepParameter> outputs = Map.of();
        if (raw.containsKey("outputs")) {
            outputs = parseParameters((Map<String, Object>) raw.get("outputs"));
        }

        InvokeBinding invoke = parseInvoke(raw.get("invoke"));

        return new StepDefinition(name, description, inputs, outputs, invoke);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, StepParameter> parseParameters(Map<String, Object> raw) {
        Map<String, StepParameter> params = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            params.put(entry.getKey(), parseParameter((Map<String, Object>) entry.getValue()));
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    static StepParameter parseParameter(Map<String, Object> raw) {
        String typeStr = (String) raw.get("type");
        StepParameterType type = typeStr != null ? StepParameterType.fromString(typeStr) : null;

        Boolean required = (Boolean) raw.get("required");
        Object defaultRaw = raw.get("default");
        String defaultValue = defaultRaw != null ? String.valueOf(defaultRaw) : null;

        List<String> allowedValues = (List<String>) raw.get("enum");
        String format = (String) raw.get("format");
        String description = (String) raw.get("description");

        return new StepParameter(type, required != null && required,
                defaultValue, allowedValues, format, description);
    }

    @SuppressWarnings("unchecked")
    static InvokeBinding parseInvoke(Object raw) {
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException(
                    "Invoke binding must be a map, got: " + (raw != null ? raw.getClass().getSimpleName() : "null"));
        }

        Map<String, Object> invokeMap = (Map<String, Object>) raw;

        if (invokeMap.containsKey("mcp")) {
            return new InvokeBinding.Mcp((String) invokeMap.get("mcp"));
        }
        if (invokeMap.containsKey("python")) {
            return new InvokeBinding.Python((String) invokeMap.get("python"));
        }
        if (invokeMap.containsKey("graphql")) {
            return new InvokeBinding.Graphql((String) invokeMap.get("graphql"));
        }
        if (invokeMap.containsKey("rest")) {
            return parseRestBinding(invokeMap.get("rest"));
        }
        if (invokeMap.containsKey("agent")) {
            return parseAgentBinding(invokeMap.get("agent"));
        }
        if (invokeMap.containsKey("process")) {
            return parseProcessBinding(invokeMap.get("process"));
        }

        throw new IllegalArgumentException(
                "Unknown invoke binding type. Expected one of: mcp, rest, graphql, python, agent, process. "
                + "Got keys: " + invokeMap.keySet());
    }

    @SuppressWarnings("unchecked")
    private static InvokeBinding.Rest parseRestBinding(Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        String method = (String) map.get("method");
        String url = (String) map.get("url");
        Map<String, String> headers = (Map<String, String>) map.get("headers");
        Map<String, String> body = (Map<String, String>) map.get("body");
        return new InvokeBinding.Rest(method, url, headers, body);
    }

    @SuppressWarnings("unchecked")
    private static InvokeBinding.Agent parseAgentBinding(Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        String descriptor = (String) map.get("descriptor");
        String model = (String) map.get("model");
        String timeout = (String) map.get("timeout");
        Boolean structuredOutput = (Boolean) map.getOrDefault("structured-output",
                map.get("structuredOutput"));
        return new InvokeBinding.Agent(descriptor, model, timeout,
                structuredOutput != null && structuredOutput);
    }

    @SuppressWarnings("unchecked")
    private static InvokeBinding.Process parseProcessBinding(Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        String command = (String) map.get("command");
        List<String> args = (List<String>) map.get("args");
        String output = (String) map.get("output");
        String timeout = (String) map.get("timeout");
        Map<String, String> env = (Map<String, String>) map.get("env");
        String workingDir = (String) map.getOrDefault("working-dir",
                map.get("workingDir"));
        String onError = (String) map.getOrDefault("on-error",
                map.get("onError"));
        return new InvokeBinding.Process(command, args, output, timeout, env, workingDir, onError);
    }
}
