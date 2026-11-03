package io.casehub.yaml.step.plugin;

import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.ParameterType;
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
                .input("_subject", ParameterType.EXPRESSION, true)
                .absorbSiblings(true)
                .execute(new AssertThatPlugin())
                .build();
    }

    @Override
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        Object rawSubject = params.get("_subject");
        Object subject = rawSubject;
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

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s + "\"";
        return String.valueOf(value);
    }
}
