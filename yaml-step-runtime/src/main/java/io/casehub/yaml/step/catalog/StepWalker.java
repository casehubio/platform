package io.casehub.yaml.step.catalog;

import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.StepCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StepWalker {

    private static final Set<String> RESERVED_KEYS = Set.of(
            "step", "invoke",
            "when", "on-success", "on-failure", "forEach", "loop",
            "retry", "timeout", "delay", "on-error", "trigger",
            "transform", "signal", "publish", "transition",
            "parallel", "semaphore", "barrier", "quorum", "race");

    private StepWalker() {}

    public static List<ResolvedStep> resolve(
            List<Map<String, Object>> steps, StepCatalog catalog) {
        List<ResolvedStep> result = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            result.add(resolveOne(steps.get(i), catalog, i));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static ResolvedStep resolveOne(
            Map<String, Object> step, StepCatalog catalog, int index) {
        if (step.isEmpty()) {
            throw new IllegalArgumentException(
                    "Step " + index + ": empty step map");
        }

        Map<String, Object> decorators = new LinkedHashMap<>();
        Map<String, Object> invokeSpec = null;
        String matchedAction = null;
        CatalogEntry matchedEntry = null;
        Map<String, Object> actionParams = null;

        for (Map.Entry<String, Object> e : step.entrySet()) {
            String key = e.getKey();

            if ("invoke".equals(key)) {
                invokeSpec = (Map<String, Object>) e.getValue();
            } else if ("step".equals(key)) {
                // step label — not a decorator or action
            } else if (RESERVED_KEYS.contains(key)) {
                decorators.put(key, e.getValue());
            } else {
                var entry = catalog.resolve(key);
                if (entry.isPresent()) {
                    if (matchedAction != null) {
                        throw new IllegalArgumentException(
                                "Step " + index + ": ambiguous — multiple action keys matched: '"
                                + matchedAction + "' and '" + key + "'");
                    }
                    matchedAction = key;
                    matchedEntry = entry.get();
                    actionParams = e.getValue() instanceof Map
                            ? (Map<String, Object>) e.getValue()
                            : Map.of();
                } else {
                    throw new IllegalArgumentException(
                            "Step " + index + ": unknown key '" + key
                            + "'. Available actions: " + catalog.availableActions());
                }
            }
        }

        if (matchedEntry != null) {
            return new ResolvedStep.PluginStep(matchedEntry, actionParams, decorators);
        }
        if (invokeSpec != null) {
            return new ResolvedStep.InvokeStep(invokeSpec, decorators);
        }

        throw new IllegalArgumentException(
                "Step " + index + ": no action key found. Available actions: "
                + catalog.availableActions());
    }
}
