package io.casehub.yaml.core.step;

import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeclarationParser {

    private DeclarationParser() {}

    @SuppressWarnings("unchecked")
    public static DeclarationFile parse(Map<String, Object> yaml) {
        String namespace = (String) yaml.getOrDefault("namespace", "");
        Map<String, Object> actions = (Map<String, Object>) yaml.get("actions");
        if (actions == null) {
            return new DeclarationFile(namespace, Map.of());
        }

        Map<String, Declaration> parsed = new LinkedHashMap<>();
        for (var entry : actions.entrySet()) {
            parsed.put(entry.getKey(),
                    parseAction(entry.getKey(), (Map<String, Object>) entry.getValue()));
        }
        return new DeclarationFile(namespace, parsed);
    }

    @SuppressWarnings("unchecked")
    public static Declaration parseAction(String name, Map<String, Object> raw) {
        String description = (String) raw.get("description");

        Map<String, Parameter> inputs = Map.of();
        if (raw.containsKey("inputs")) {
            inputs = parseParameters((Map<String, Object>) raw.get("inputs"));
        }

        Map<String, Parameter> outputs = Map.of();
        if (raw.containsKey("outputs")) {
            outputs = parseParameters((Map<String, Object>) raw.get("outputs"));
        }

        InvokeBinding invoke = parseInvoke(raw.get("invoke"));

        return new Declaration(name, description, inputs, outputs, invoke);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Parameter> parseParameters(Map<String, Object> raw) {
        Map<String, Parameter> params = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            params.put(entry.getKey(), parseParameter((Map<String, Object>) entry.getValue()));
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    static Parameter parseParameter(Map<String, Object> raw) {
        String typeStr = (String) raw.get("type");
        ParameterType type = typeStr != null ? ParameterType.fromString(typeStr) : null;

        Boolean required = (Boolean) raw.get("required");
        Object defaultRaw = raw.get("default");
        String defaultValue = defaultRaw != null ? String.valueOf(defaultRaw) : null;

        List<String> allowedValues = (List<String>) raw.get("enum");
        String format = (String) raw.get("format");
        String description = (String) raw.get("description");

        return new Parameter(type, required != null && required,
                defaultValue, allowedValues, format, description);
    }

    @SuppressWarnings("unchecked")
    public static InvokeBinding parseInvoke(Object raw) {
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException(
                    "Invoke binding must be a map, got: " + (raw != null ? raw.getClass().getSimpleName() : "null"));
        }

        Map<String, Object> invokeMap = (Map<String, Object>) raw;

        if (invokeMap.containsKey("mcp")) {
            return new InvokeBinding.Mcp((String) invokeMap.get("mcp"));
        }
        if (invokeMap.containsKey("python")) {
            return new InvokeBinding.Script(InvokeBinding.Script.PYTHON, (String) invokeMap.get("python"), null, null, null);
        }
        if (invokeMap.containsKey("node")) {
            return new InvokeBinding.Script(InvokeBinding.Script.NODE, (String) invokeMap.get("node"), null, null, null);
        }
        if (invokeMap.containsKey("script")) {
            return parseScriptBinding(invokeMap.get("script"));
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
        if (invokeMap.containsKey("aria")) {
            return new InvokeBinding.Aria((String) invokeMap.get("aria"));
        }
        if ("aria".equals(invokeMap.get("kind"))) {
            return new InvokeBinding.Aria((String) invokeMap.get("action"));
        }
        if (invokeMap.containsKey("agent-session-open")) {
            return parseSessionOpenBinding(invokeMap.get("agent-session-open"));
        }
        if (invokeMap.containsKey("agent-query")) {
            return parseSessionQueryBinding(invokeMap.get("agent-query"));
        }
        if (invokeMap.containsKey("agent-session-clear")) {
            return parseSessionClearBinding(invokeMap.get("agent-session-clear"));
        }
        if (invokeMap.containsKey("agent-session-close")) {
            return parseSessionCloseBinding(invokeMap.get("agent-session-close"));
        }

        throw new IllegalArgumentException(
                "Unknown invoke binding type. Expected one of: mcp, rest, graphql, python, agent, process, aria, "
                + "agent-session-open, agent-query, agent-session-clear, agent-session-close. "
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
        Map<String, Object> modelQuery = (Map<String, Object>) map.getOrDefault("model-query",
                map.get("modelQuery"));
        String system = (String) map.get("system");
        String user = (String) map.get("user");
        String timeout = (String) map.get("timeout");
        Boolean structuredOutput = (Boolean) map.getOrDefault("structured-output",
                map.get("structuredOutput"));
        String pool = (String) map.get("pool");
        List<Object> modelChain = (List<Object>) map.getOrDefault("model-chain",
                map.get("modelChain"));
        Object responseFormat = map.getOrDefault("response-format",
                map.get("responseFormat"));
        return new InvokeBinding.Agent(descriptor, model, modelQuery, system, user, timeout,
                structuredOutput != null && structuredOutput,
                pool, modelChain, responseFormat);
    }

    @SuppressWarnings("unchecked")
    private static InvokeBinding.Script parseScriptBinding(Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        return new InvokeBinding.Script(
                (String) map.get("runtime"),
                (String) map.get("script"),
                (String) map.get("timeout"),
                (String) map.get("workingDir"),
                (Map<String, String>) map.get("env"));
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

    @SuppressWarnings("unchecked")
    private static InvokeBinding.SessionOpen parseSessionOpenBinding(Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        return new InvokeBinding.SessionOpen(
                (String) map.get("session"),
                (String) map.get("system"),
                (String) map.get("model"),
                (Map<String, Object>) map.getOrDefault("model-query", map.get("modelQuery")),
                (List<Object>) map.getOrDefault("model-chain", map.get("modelChain")),
                (String) map.get("pool"),
                (String) map.get("timeout"),
                (String) map.getOrDefault("checkout-timeout", map.get("checkoutTimeout")));
    }

    @SuppressWarnings("unchecked")
    private static InvokeBinding.SessionQuery parseSessionQueryBinding(Object raw) {
        Map<String, Object> map      = (Map<String, Object>) raw;
        Boolean             priority = (Boolean) map.get("priority");
        return new InvokeBinding.SessionQuery(
                (String) map.get("session"),
                (String) map.get("user"),
                map.getOrDefault("response-format", map.get("responseFormat")),
                (String) map.get("output"),
                priority != null && priority);
    }

    @SuppressWarnings("unchecked")
    private static InvokeBinding.SessionClear parseSessionClearBinding(Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        return new InvokeBinding.SessionClear((String) map.get("session"));
    }

    @SuppressWarnings("unchecked")
    private static InvokeBinding.SessionClose parseSessionCloseBinding(Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
        return new InvokeBinding.SessionClose((String) map.get("session"));
    }

}
