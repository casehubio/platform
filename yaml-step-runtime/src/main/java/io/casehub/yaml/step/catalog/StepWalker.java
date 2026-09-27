package io.casehub.yaml.step.catalog;

import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.StepCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class StepWalker {

    private static final Logger LOG = Logger.getLogger(StepWalker.class.getName());

    private static final Set<String> RESERVED_KEYS = Set.of(
            "step", "invoke",
            "if", "then", "else", "match", "cases", "block",
            "try", "catch", "finally", "select",
            "on-success", "on-failure", "forEach", "loop",
            "retry", "timeout", "delay", "on-error", "trigger",
            "transform", "signal", "publish", "transition",
            "parallel", "semaphore", "barrier", "quorum", "race");

    private static final Set<String> STRUCTURAL_COMPANIONS = Set.of("then", "else", "cases", "catch", "finally");
    static final         int         MAX_DEPTH             = 32;


    private StepWalker() {}

    public static List<ResolvedStep> resolve(
            List<Map<String, Object>> steps, StepCatalog catalog) {
        List<ResolvedStep> result = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            result.add(resolveOne(steps.get(i), catalog, i, 0, "root"));
        }
        return result;
    }

    private static List<ResolvedStep> resolve(
            List<Map<String, Object>> steps, StepCatalog catalog, int depth, String path) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Maximum nesting depth (" + MAX_DEPTH + ") exceeded at: " + path);
        }
        List<ResolvedStep> result = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            result.add(resolveOne(steps.get(i), catalog, i, depth, path));
        }
        return result;
    }


    @SuppressWarnings("unchecked")
    private static ResolvedStep resolveOne(
            Map<String, Object> step, StepCatalog catalog, int index, int depth, String path) {
        if (step.isEmpty()) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": empty step map");
        }

        Map<String, Object> decorators    = new LinkedHashMap<>();
        Map<String, Object> companions    = new LinkedHashMap<>();
        Map<String, Object> invokeSpec    = null;
        String              matchedAction = null;
        CatalogEntry        matchedEntry  = null;
        Map<String, Object> actionParams  = null;

        String  structuralType  = null;
        Object  structuralValue = null;
        Object  ifValue         = null;
        boolean hasIf           = false;
        Object  matchValue      = null;
        boolean hasMatch        = false;

        for (Map.Entry<String, Object> e : step.entrySet()) {
            String key = e.getKey();

            if ("step".equals(key)) {
                // label — skip
            } else if ("invoke".equals(key)) {
                invokeSpec = (Map<String, Object>) e.getValue();
            } else if ("block".equals(key) || "parallel".equals(key) || "try".equals(key) || "select".equals(key)) {
                structuralType  = key;
                structuralValue = e.getValue();
            } else if ("if".equals(key)) {
                hasIf   = true;
                ifValue = e.getValue();
                decorators.put("if", ifValue);
            } else if ("match".equals(key)) {
                hasMatch   = true;
                matchValue = e.getValue();
            } else if (STRUCTURAL_COMPANIONS.contains(key)) {
                companions.put(key, e.getValue());
            } else if (RESERVED_KEYS.contains(key)) {
                decorators.put(key, e.getValue());
            } else {
                var entry = catalog.resolve(key);
                if (entry.isPresent()) {
                    if (matchedAction != null) {
                        throw new IllegalArgumentException(
                                path + " → Step " + index + ": ambiguous — multiple action keys matched: '"
                                + matchedAction + "' and '" + key + "'");
                    }
                    matchedAction = key;
                    matchedEntry  = entry.get();
                    actionParams  = e.getValue() instanceof Map
                                    ? (Map<String, Object>) e.getValue()
                                    : Map.of();
                } else {
                    throw new IllegalArgumentException(
                            path + " → Step " + index + ": unknown key '" + key
                            + "'. Available actions: " + catalog.availableActions());
                }
            }
        }

        if (hasIf && companions.containsKey("then")) {
            decorators.remove("if");
            structuralType = "if";
        }

        if (hasMatch && companions.containsKey("cases")) {
            if (structuralType != null) {
                throw new IllegalArgumentException(
                        path + " → Step " + index + ": ambiguous — structural keyword '" + structuralType
                        + "' and 'match' both present");
            }
            structuralType = "match";
        }

        if (structuralType != null && matchedAction != null) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": ambiguous — structural keyword '" + structuralType
                    + "' and action key '" + matchedAction + "' both present");
        }
        if (structuralType != null && invokeSpec != null) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": ambiguous — structural keyword '" + structuralType
                    + "' and 'invoke' both present");
        }

        if (companions.containsKey("then") && !hasIf) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": 'then' requires an 'if' condition");
        }
        if (companions.containsKey("else") && !companions.containsKey("then")) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": 'else' requires 'then'");
        }
        if (companions.containsKey("cases") && !"match".equals(structuralType)) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": 'cases' requires 'match'");
        }
        if (hasMatch && !companions.containsKey("cases")) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": 'match' requires 'cases'");
        }
        if (companions.containsKey("catch") && !"try".equals(structuralType)) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": 'catch' requires 'try'");
        }
        if (companions.containsKey("finally") && !"try".equals(structuralType)) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": 'finally' requires 'try'");
        }

        String stepPath = path + " → Step " + index;
        if ("if".equals(structuralType)) {
            var thenSteps = (List<Map<String, Object>>) companions.get("then");
            var elseSteps = (List<Map<String, Object>>) companions.get("else");
            return new ResolvedStep.IfElseStep(
                    (String) ifValue,
                    resolve(thenSteps, catalog, depth + 1, stepPath + " → then"),
                    elseSteps != null ? resolve(elseSteps, catalog, depth + 1, stepPath + " → else") : null,
                    decorators);
        }
        if ("block".equals(structuralType)) {
            return new ResolvedStep.BlockStep(
                    resolve((List<Map<String, Object>>) structuralValue, catalog, depth + 1, stepPath + " → block"),
                    decorators);
        }
        if ("match".equals(structuralType)) {
            var cases = (List<Map<String, Object>>) companions.get("cases");
            return new ResolvedStep.MatchStep(
                    (String) matchValue,
                    resolveMatchCases(cases, catalog, index, depth, stepPath),
                    decorators);
        }
        if ("parallel".equals(structuralType)) {
            return new ResolvedStep.ParallelStep(
                    resolve((List<Map<String, Object>>) structuralValue, catalog, depth + 1, stepPath + " → parallel"),
                    decorators);
        }
        if ("try".equals(structuralType)) {
            var trySteps     = (List<Map<String, Object>>) structuralValue;
            var catchSteps   = (List<Map<String, Object>>) companions.get("catch");
            var finallySteps = (List<Map<String, Object>>) companions.get("finally");
            return new ResolvedStep.TryCatchFinallyStep(
                    resolve(trySteps, catalog, depth + 1, stepPath + " → try"),
                    catchSteps != null ? resolve(catchSteps, catalog, depth + 1, stepPath + " → catch") : null,
                    finallySteps != null ? resolve(finallySteps, catalog, depth + 1, stepPath + " → finally") : null,
                    decorators);
        }
        if ("select".equals(structuralType)) {
            var branchMaps = (List<Map<String, Object>>) structuralValue;
            return new ResolvedStep.SelectStep(
                    resolveSelectBranches(branchMaps, catalog, depth, stepPath),
                    decorators);
        }
        if (matchedEntry != null) {
            return new ResolvedStep.PluginStep(matchedEntry, actionParams, decorators);
        }
        if (invokeSpec != null) {
            return new ResolvedStep.InvokeStep(invokeSpec, decorators);
        }

        throw new IllegalArgumentException(
                stepPath + ": no step type identified. Available actions: "
                + catalog.availableActions());
    }

    @SuppressWarnings("unchecked")
    private static List<ResolvedMatchCase> resolveMatchCases(
            List<Map<String, Object>> cases, StepCatalog catalog, int stepIndex, int depth, String path) {
        List<ResolvedMatchCase> result = new ArrayList<>(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            Map<String, Object> caseMap    = cases.get(i);
            boolean             isDefault  = caseMap.containsKey("default");
            boolean             hasPattern = caseMap.containsKey("pattern");

            if (isDefault && hasPattern) {
                throw new IllegalArgumentException(
                        path + ", case " + i
                        + ": pattern and default are mutually exclusive in a case entry");
            }
            if (!isDefault && !hasPattern) {
                throw new IllegalArgumentException(
                        path + ", case " + i
                        + ": case entry must contain either 'pattern' or 'default'");
            }

            if (isDefault && i < cases.size() - 1) {
                throw new IllegalArgumentException(
                        path + ": default must be the last case");
            }

            String casePath = path + " → case " + i;
            if (isDefault) {
                var steps = (List<Map<String, Object>>) caseMap.get("default");
                result.add(new ResolvedMatchCase(
                        new io.casehub.yaml.core.step.MatchPattern.DefaultPattern(),
                        null,
                        resolve(steps, catalog, depth + 1, casePath + " → default")));
            } else {
                var                                    patternObj = caseMap.get("pattern");
                io.casehub.yaml.core.step.MatchPattern pattern;
                if (patternObj instanceof Map) {
                    pattern = new io.casehub.yaml.core.step.MatchPattern.StructuralPattern(
                            (Map<String, Object>) patternObj);
                } else if (patternObj instanceof List) {
                    pattern = new io.casehub.yaml.core.step.MatchPattern.AnyOfPattern(
                            (List<Object>) patternObj);
                } else {
                    pattern = new io.casehub.yaml.core.step.MatchPattern.ValuePattern(patternObj);
                }
                String guard = caseMap.containsKey("guard")
                               ? String.valueOf(caseMap.get("guard")) : null;
                var steps = caseMap.containsKey("steps")
                            ? (List<Map<String, Object>>) caseMap.get("steps")
                            : List.<Map<String, Object>>of();
                result.add(new ResolvedMatchCase(pattern, guard, resolve(steps, catalog, depth + 1, casePath)));
            }
        }

        boolean hasDefault = result.stream()
                                   .anyMatch(c -> c.pattern() instanceof io.casehub.yaml.core.step.MatchPattern.DefaultPattern);
        if (!hasDefault) {
            LOG.warning(path + ": match/cases has no default case — "
                        + "unmatched values will be silently skipped");
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<ResolvedStep.SelectBranch> resolveSelectBranches(
            List<Map<String, Object>> branches, StepCatalog catalog, int depth, String path) {
        List<ResolvedStep.SelectBranch> result = new ArrayList<>(branches.size());
        for (int i = 0; i < branches.size(); i++) {
            Map<String, Object> branchMap  = branches.get(i);
            String              branchPath = path + " → select[" + i + "]";
            String              type;
            String              name;
            if (branchMap.containsKey("subscribe")) {
                type = "subscribe";
                Object subSpec = branchMap.get("subscribe");
                if (subSpec instanceof Map<?, ?> subMap) {
                    name = String.valueOf(subMap.get("channel"));
                } else {
                    name = String.valueOf(subSpec);
                }
            } else if (branchMap.containsKey("wait")) {
                type = "wait";
                name = String.valueOf(branchMap.get("wait"));
            } else {
                throw new IllegalArgumentException(
                        branchPath + ": select branch must contain 'subscribe' or 'wait'");
            }
            var steps = branchMap.containsKey("steps")
                        ? (List<Map<String, Object>>) branchMap.get("steps")
                        : List.<Map<String, Object>>of();
            result.add(new ResolvedStep.SelectBranch(
                    type, name, resolve(steps, catalog, depth + 1, branchPath)));
        }
        return result;
    }

}
