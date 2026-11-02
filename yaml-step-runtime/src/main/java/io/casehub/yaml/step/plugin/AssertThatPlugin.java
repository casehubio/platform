package io.casehub.yaml.step.plugin;

import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AssertThatPlugin implements Action {

    private static final Set<String> RESERVED_PARAMS = Set.of("_subject", "message");

    public static Definition definition() {
        return Definition.of("assertThat")
                .description("Behavioural assertion with matchers")
                .absorbSiblings(true)
                .execute(new AssertThatPlugin())
                .build();
    }

    @Override
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        Object rawSubject = params.get("_subject");
        Object subject = resolveSubject(rawSubject, services);
        String message = params.get("message") != null ? String.valueOf(params.get("message")) : null;

        List<MatchResult> failures = new ArrayList<>();
        for (var entry : params.entrySet()) {
            String key = entry.getKey();
            if (RESERVED_PARAMS.contains(key)) continue;
            var matcher = MatcherRegistry.resolve(key);
            if (matcher.isEmpty()) continue;
            var result = matcher.get().apply(subject, entry.getValue());
            if (!result.passed()) failures.add(result);
        }

        if (failures.isEmpty()) {
            return Result.of(Map.of("passed", true));
        }

        var sb = new StringBuilder();
        sb.append("Subject: ").append(rawSubject).append("\n");
        sb.append("Actual: ").append(formatValue(subject)).append("\n");
        sb.append("Failed:\n");
        for (var f : failures) {
            sb.append("  ").append(f.description()).append("\n");
        }
        if (message != null) {
            sb.append(message);
        }

        return Result.failed(sb.toString());
    }

    private Object resolveSubject(Object rawSubject, ServiceRegistry services) {
        if (rawSubject == null) return null;
        String subjectStr = String.valueOf(rawSubject);
        if (!subjectStr.startsWith("${") || !subjectStr.endsWith("}")) {
            return rawSubject;
        }
        ExpressionEngineRegistry engines;
        try {
            engines = services.lookup(ExpressionEngineRegistry.class);
        } catch (IllegalArgumentException e) {
            return rawSubject;
        }

        String engineType = engines.resolveDefault(ExpressionContext.CONDITION);
        if (engineType == null) return rawSubject;

        try {
            String expression = subjectStr.substring(2, subjectStr.length() - 1);
            Map<String, Object> context;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> testContext = (Map<String, Object>) services.lookup(Map.class);
                context = testContext;
            } catch (IllegalArgumentException e) {
                context = Map.of();
            }
            @SuppressWarnings("unchecked")
            var compiled = engines.compile(engineType, expression, Map.class, Object.class);
            return compiled.eval(context);
        } catch (Exception e) {
            return rawSubject;
        }
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s + "\"";
        return String.valueOf(value);
    }
}
