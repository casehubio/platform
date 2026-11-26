package io.casehub.yaml.step.runner;

import io.casehub.yaml.plugin.api.FailureCategory;
import io.casehub.yaml.plugin.api.Result;

import java.util.List;
import java.util.Map;

public final class UnmetRequirementHandler implements FailureRecoveryHandler {

    @Override
    public boolean canRecover(Result.Failure failure) {
        return FailureCategory.UNMET_REQUIREMENT.equals(failure.category());
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecoveryAction diagnose(Result.Failure failure, RecoveryContext context) {
        var options = extractOptions(failure.detail());
        if (options == null || options.isEmpty()) {
            return new RecoveryAction.Unrecoverable(failure.message(), failure.detail());
        }

        for (var option : options) {
            if ("provider".equals(option.get("type"))
                    && option.containsKey("setup-playbook")) {
                String path = (String) option.get("setup-playbook");
                var vars = Map.<String, Object>of(
                        "capability", failure.detail().getOrDefault("capability", ""),
                        "sub", failure.detail().getOrDefault("sub", List.of()),
                        "provider", option.getOrDefault("connector-id", ""));
                return new RecoveryAction.RunPlaybook(path, vars);
            }
        }

        return new RecoveryAction.Unrecoverable(
                "Capability '" + failure.detail().get("capability") + "' requires manual resolution",
                Map.of(
                        "capability", failure.detail().getOrDefault("capability", ""),
                        "sub", failure.detail().getOrDefault("sub", List.of()),
                        "manualOptions", options.stream()
                                .filter(o -> "manual".equals(o.get("type")))
                                .toList()));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractOptions(Map<String, Object> detail) {
        Object raw = detail.get("options");
        if (raw instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?>) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return null;
    }
}
