package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.annotation.AnnotationMap;
import io.casehub.yaml.plugin.api.PluginRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class Walker {

    private static final Logger LOG = Logger.getLogger(Walker.class.getName());

    private static final Set<String> RESERVED_KEYS = StepKeywords.RESERVED_KEYS;

    private static final Set<String> STRUCTURAL_COMPANIONS = Set.of("then", "else", "cases", "catch", "finally");
    private static final Set<String> PROMOTABLE_DECORATORS = Set.of(
            "forEach", "retry", "timeout", "when", "mutex", "loop");

    private static final Set<String> REMOVED_KEYS = Set.of("steps", "do");
    static final         int         MAX_DEPTH             = 32;
    private static final Set<String> MIGRATED_DECORATORS   = Set.of(
            "timeout", "retry", "delay", "priority", "resource", "background",
            "on-error", "trigger", "semaphore", "at", "cancel", "mutex");


    private Walker() {}

    private static final Set<String> COMBINED_KEY_PREFIXES = new java.util.HashSet<>();
    static {
        COMBINED_KEY_PREFIXES.addAll(PROMOTABLE_DECORATORS);
        COMBINED_KEY_PREFIXES.addAll(java.util.List.of(
                "block", "parallel", "try", "select", "race", "if", "match",
                "barrier", "quorum"));
    }

    private static String matchCombinedKey(String key) {
        int spaceIdx = key.indexOf(' ');
        if (spaceIdx < 0) return null;
        String prefix = key.substring(0, spaceIdx);
        return COMBINED_KEY_PREFIXES.contains(prefix) ? prefix : null;
    }

    private static Map<String, Object> stripKeys(
            Map<String, Object> map, Set<String> keysToStrip) {
        var result = new LinkedHashMap<String, Object>();
        for (var e : map.entrySet()) {
            if (!keysToStrip.contains(e.getKey())) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    public static List<ResolvedStep> resolve(
            List<Map<String, Object>> steps, PluginRegistry registry) {
        return resolve(steps, registry, AnnotationMap.empty());
    }

    public static List<ResolvedStep> resolve(
            List<Map<String, Object>> steps, PluginRegistry registry, AnnotationMap annotations) {
        Set<String>        seenNames = new java.util.HashSet<>();
        List<ResolvedStep> result    = resolve(steps, registry, 0, "root", seenNames, annotations, "", 0);
        validateBarrierQuorumReferences(result, seenNames);
        result = io.casehub.yaml.step.eval.FusionPass.fuse(result, registry);
        return result;
    }


    private static List<ResolvedStep> resolve(
            List<Map<String, Object>> steps, PluginRegistry registry, int depth, String path, Set<String> seenNames) {
        return resolve(steps, registry, depth, path, seenNames, AnnotationMap.empty(), "", 0);
    }

    private static List<ResolvedStep> resolve(
            List<Map<String, Object>> steps, PluginRegistry registry, int depth, String path,
            Set<String> seenNames, AnnotationMap annotations, String annPrefix, int annOffset) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Maximum nesting depth (" + MAX_DEPTH + ") exceeded at: " + path);
        }
        List<ResolvedStep> result = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            String                                           stepKey  = annPrefix + (annOffset + i);
            ResolvedStep                                     step     = resolveOne(steps.get(i), registry, i, depth, path, seenNames, annotations, stepKey);
            List<io.casehub.yaml.core.annotation.Annotation> stepAnns = annotations.forStep(stepKey);
            if (!stepAnns.isEmpty()) {
                step = mergeAnnotationDecorators(step, stepAnns, annOffset + i);
                step = withAnnotations(step, stepAnns);
            }
            result.add(step);
        }
        return result;
    }

    private static ResolvedStep withAnnotations(ResolvedStep step, List<io.casehub.yaml.core.annotation.Annotation> anns) {
        return switch (step) {
            case ResolvedStep.PluginStep s -> new ResolvedStep.PluginStep(s.name(), s.actionName(), s.params(), s.decorators(), anns);
            case ResolvedStep.InvokeStep s -> new ResolvedStep.InvokeStep(s.name(), s.invokeSpec(), s.decorators(), anns);
            case ResolvedStep.BlockStep s -> new ResolvedStep.BlockStep(s.name(), s.steps(), s.decorators(), anns);
            case ResolvedStep.IfElseStep s -> new ResolvedStep.IfElseStep(s.name(), s.condition(), s.thenSteps(), s.elseSteps(), s.decorators(), anns);
            case ResolvedStep.MatchStep s -> new ResolvedStep.MatchStep(s.name(), s.scrutinee(), s.cases(), s.decorators(), anns);
            case ResolvedStep.ParallelStep s -> new ResolvedStep.ParallelStep(s.name(), s.steps(), s.advanceAfter(), s.cancelRemaining(), s.collectionTimeout(), s.decorators(), anns);
            case ResolvedStep.TryCatchFinallyStep s -> new ResolvedStep.TryCatchFinallyStep(s.name(), s.trySteps(), s.catchSteps(), s.finallySteps(), s.decorators(), anns);
            case ResolvedStep.SelectStep s -> new ResolvedStep.SelectStep(s.name(), s.branches(), s.decorators(), anns);
            case ResolvedStep.BarrierStep s -> new ResolvedStep.BarrierStep(s.name(), s.awaitSteps(), s.timeout(), s.decorators(), anns);
            case ResolvedStep.QuorumStep s -> new ResolvedStep.QuorumStep(s.name(), s.required(), s.ofSteps(), s.timeout(), s.decorators(), anns);
            case ResolvedStep.ControlStep s -> new ResolvedStep.ControlStep(s.name(), s.target(), s.activate(), s.decorators(), anns);
            case ResolvedStep.RaceStep s -> new ResolvedStep.RaceStep(s.name(), s.branches(), s.decorators(), anns);
            case ResolvedStep.FusedPipelineStep s -> s;
        };
    }

    private static ResolvedStep mergeAnnotationDecorators(ResolvedStep step, List<io.casehub.yaml.core.annotation.Annotation> anns, int index) {
        Map<String, Object> mergedDecorators = null;
        for (var ann : anns) {
            if (MIGRATED_DECORATORS.contains(ann.name())) {
                if (mergedDecorators == null) {
                    mergedDecorators = new LinkedHashMap<>(step.decorators());
                }
                if (mergedDecorators.containsKey(ann.name())) {
                    LOG.warning("Step " + index + ": decorator key '" + ann.name()
                                + "' is deprecated — use #@" + ann.name() + "() annotation instead");
                }
                Object value;
                if (ann.args().isEmpty()) {
                    value = true;
                } else {
                    String raw = ann.stringArg(0);
                    value = parseDecoratorValue(raw);
                }
                mergedDecorators.put(ann.name(), value);
            }
        }
        if (mergedDecorators == null) {return step;}
        return withDecorators(step, mergedDecorators);
    }

    private static Object parseDecoratorValue(String raw) {
        if (raw == null) {return true;}
        try {return Integer.parseInt(raw);} catch (NumberFormatException ignored) {}
        try {return Double.parseDouble(raw);} catch (NumberFormatException ignored) {}
        if ("true".equalsIgnoreCase(raw)) {return true;}
        if ("false".equalsIgnoreCase(raw)) {return false;}
        return raw;
    }


    private static ResolvedStep withDecorators(ResolvedStep step, Map<String, Object> decorators) {
        return switch (step) {
            case ResolvedStep.PluginStep s -> new ResolvedStep.PluginStep(s.name(), s.actionName(), s.params(), decorators);
            case ResolvedStep.InvokeStep s -> new ResolvedStep.InvokeStep(s.name(), s.invokeSpec(), decorators);
            case ResolvedStep.BlockStep s -> new ResolvedStep.BlockStep(s.name(), s.steps(), decorators);
            case ResolvedStep.IfElseStep s -> new ResolvedStep.IfElseStep(s.name(), s.condition(), s.thenSteps(), s.elseSteps(), decorators);
            case ResolvedStep.MatchStep s -> new ResolvedStep.MatchStep(s.name(), s.scrutinee(), s.cases(), decorators);
            case ResolvedStep.ParallelStep s -> new ResolvedStep.ParallelStep(s.name(), s.steps(), s.advanceAfter(), s.cancelRemaining(), s.collectionTimeout(), decorators);
            case ResolvedStep.TryCatchFinallyStep s -> new ResolvedStep.TryCatchFinallyStep(s.name(), s.trySteps(), s.catchSteps(), s.finallySteps(), decorators);
            case ResolvedStep.SelectStep s -> new ResolvedStep.SelectStep(s.name(), s.branches(), decorators);
            case ResolvedStep.BarrierStep s -> new ResolvedStep.BarrierStep(s.name(), s.awaitSteps(), s.timeout(), decorators);
            case ResolvedStep.QuorumStep s -> new ResolvedStep.QuorumStep(s.name(), s.required(), s.ofSteps(), s.timeout(), decorators);
            case ResolvedStep.ControlStep s -> new ResolvedStep.ControlStep(s.name(), s.target(), s.activate(), decorators);
            case ResolvedStep.RaceStep s -> new ResolvedStep.RaceStep(s.name(), s.branches(), decorators);
            case ResolvedStep.FusedPipelineStep s -> s;
        };
    }


    @SuppressWarnings("unchecked")
    private static ResolvedStep resolveOne(
            Map<String, Object> step, PluginRegistry registry, int index, int depth, String path,
            Set<String> seenNames, AnnotationMap annotations, String stepKey) {
        if (step.isEmpty()) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": empty step map");
        }

        for (String key : step.keySet()) {
            if (REMOVED_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        path + " → Step " + index + ": '" + key
                        + "' is no longer valid — use inline sibling keys, or 'block' for multiple actions");
            }
        }

        Map<String, Object>                   decorators    = new LinkedHashMap<>();
        Map<String, Object>                   companions    = new LinkedHashMap<>();
        Map<String, Object>                   invokeSpec    = null;
        String                                matchedAction = null;
        Map<String, Object>                   actionParams  = null;
        io.casehub.yaml.plugin.api.Definition matchedDef    = null;
        List<Map.Entry<String, Object>>       unknownKeys   = new ArrayList<>();

        String  structuralType  = null;
        Object  structuralValue = null;
        Object  ifValue         = null;
        boolean hasIf           = false;
        Object  matchValue      = null;
        boolean hasMatch        = false;
        String  stepName        = null;
        boolean stepFromKey     = false;

        for (Map.Entry<String, Object> e : step.entrySet()) {
            String key = e.getKey();

            if ("step".equals(key)) {
                stepName = (String) e.getValue();
                stepFromKey = true;
            } else if ("invoke".equals(key)) {
                invokeSpec = (Map<String, Object>) e.getValue();
            } else if ("block".equals(key) || "parallel".equals(key) || "try".equals(key) || "select".equals(key) || "barrier".equals(key) || "quorum".equals(key) || "race".equals(key) || "pause".equals(key) || "resume".equals(key)) {
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
            } else if (PROMOTABLE_DECORATORS.contains(key)
                       && (e.getValue() instanceof List
                           || (e.getValue() instanceof Map<?, ?> pm && pm.size() == 1
                               && pm.values().iterator().next() instanceof List))) {
                if (structuralType != null) {
                    throw new IllegalArgumentException(
                            path + " → Step " + index + ": step has both structural type '"
                            + structuralType + "' and structural decorator '" + key + "'");
                }
                structuralType  = key;
                structuralValue = e.getValue();
            } else if (RESERVED_KEYS.contains(key)) {
                decorators.put(key, e.getValue());
            } else if (matchCombinedKey(key) != null && (e.getValue() instanceof List || (e.getValue() instanceof Map && !PROMOTABLE_DECORATORS.contains(matchCombinedKey(key))))) {
                if (structuralType != null) {
                    throw new IllegalArgumentException(
                            path + " → Step " + index + ": step has both structural type '"
                            + structuralType + "' and structural decorator '" + key + "'");
                }
                int    spaceIdx = key.indexOf(' ');
                String prefix   = key.substring(0, spaceIdx);
                String param    = key.substring(spaceIdx + 1);
                if (PROMOTABLE_DECORATORS.contains(prefix)) {
                    structuralType  = prefix;
                    structuralValue = java.util.Map.of(param, e.getValue());
                } else {
                    if (stepName == null) {stepName = param;}
                    structuralType  = prefix;
                    structuralValue = e.getValue();
                }
            } else {
                var entry = registry.resolve(key);
                if (entry.isPresent()) {
                    if (matchedAction != null) {
                        throw new IllegalArgumentException(
                                path + " → Step " + index + ": ambiguous — multiple action keys matched: '"
                                + matchedAction + "' and '" + key + "'");
                    }
                    matchedAction = key;
                    matchedDef    = entry.get();
                    Object value = e.getValue();
                    if (value instanceof Map) {
                        actionParams = (Map<String, Object>) value;
                    } else if (value != null) {
                        actionParams = new LinkedHashMap<>();
                        actionParams.put("_subject", value);
                    } else {
                        actionParams = new LinkedHashMap<>();
                    }
                } else {
                    unknownKeys.add(e);
                }
            }
        }

        if (matchedAction != null && matchedDef != null && matchedDef.absorbSiblings() && !unknownKeys.isEmpty()) {
            if (!(actionParams instanceof LinkedHashMap)) {
                actionParams = new LinkedHashMap<>(actionParams);
            }
            for (var unk : unknownKeys) {
                actionParams.put(unk.getKey(), unk.getValue());
            }
            unknownKeys.clear();
        }

        if (!unknownKeys.isEmpty()) {
            var first = unknownKeys.get(0);
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": unknown key '" + first.getKey()
                    + "'. Available actions: " + registry.availableActions());
        }

        if (stepFromKey && structuralType != null) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": 'step' is not valid inside '" + structuralType
                    + "' — use combined-key syntax: '" + structuralType + " " + stepName
                    + ":' instead of '" + structuralType + ":' + 'step: " + stepName + "'");
        }

        if (stepName != null && !seenNames.add(stepName)) {
            throw new IllegalArgumentException(
                    path + " → Step " + index + ": duplicate step name '" + stepName + "'");
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
        if ("try".equals(structuralType) && !companions.containsKey("catch") && !companions.containsKey("finally")) {
            LOG.warning(path + " → Step " + index + ": 'try' without 'catch' or 'finally' — "
                        + "the try block has no error handling or cleanup");
        }

        String stepPath = path + " → Step " + index;
        if ("if".equals(structuralType)) {
            var thenSteps = (List<Map<String, Object>>) companions.get("then");
            var elseSteps = (List<Map<String, Object>>) companions.get("else");
            String childPrefix = stepKey + ".";
            return new ResolvedStep.IfElseStep(
                    stepName,
                    String.valueOf(ifValue),
                    resolve(thenSteps, registry, depth + 1, stepPath + " → then", seenNames, annotations, childPrefix, 0),
                    elseSteps != null ? resolve(elseSteps, registry, depth + 1, stepPath + " → else", seenNames, annotations, childPrefix, thenSteps.size()) : null,
                    decorators);
        }
        if ("block".equals(structuralType)) {
            return new ResolvedStep.BlockStep(
                    stepName,
                    resolve((List<Map<String, Object>>) structuralValue, registry, depth + 1, stepPath + " → block", seenNames, annotations, stepKey + ".", 0),
                    decorators);
        }
        if ("match".equals(structuralType)) {
            var cases = (List<Map<String, Object>>) companions.get("cases");
            return new ResolvedStep.MatchStep(
                    stepName,
                    (String) matchValue,
                    resolveMatchCases(cases, registry, index, depth, stepPath, seenNames, annotations, stepKey),
                    decorators);
        }
        if ("parallel".equals(structuralType)) {
            if (structuralValue instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                var stepList = (List<Map<String, Object>>) structuralValue;
                return new ResolvedStep.ParallelStep(
                        stepName,
                        resolve(stepList, registry, depth + 1, stepPath + " → parallel", seenNames, annotations, stepKey + ".", 0),
                        decorators);
            }
            @SuppressWarnings("unchecked")
            var policyMap = (Map<String, Object>) structuralValue;
            @SuppressWarnings("unchecked")
            var stepList = (List<Map<String, Object>>) policyMap.get("steps");
            if (stepList == null || stepList.isEmpty()) {
                throw new IllegalArgumentException(
                        "parallel map form requires a non-empty 'steps' list at " + stepPath);
            }
            var    resolved        = resolve(stepList, registry, depth + 1, stepPath + " → parallel", seenNames, annotations, stepKey + ".", 0);
            Object advanceAfterRaw = policyMap.get("advance-after");
            int    advanceAfter;
            if (advanceAfterRaw == null || "all".equals(advanceAfterRaw)) {
                advanceAfter = resolved.size();
            } else if (advanceAfterRaw instanceof Number n) {
                advanceAfter = n.intValue();
            } else {
                throw new IllegalArgumentException(
                        "advance-after must be a positive integer or 'all', got: " + advanceAfterRaw + " at " + stepPath);
            }
            boolean            cancelRemaining   = Boolean.TRUE.equals(policyMap.get("cancel-remaining"));
            java.time.Duration collectionTimeout = null;
            Object             ctRaw             = policyMap.get("collection-timeout");
            if (ctRaw != null) {
                collectionTimeout = io.casehub.yaml.core.orchestration.DurationParser.parse(ctRaw.toString());
            }
            return new ResolvedStep.ParallelStep(
                    stepName, resolved, advanceAfter, cancelRemaining, collectionTimeout, decorators);
        }
        if ("try".equals(structuralType)) {
            var trySteps     = (List<Map<String, Object>>) structuralValue;
            var catchSteps   = (List<Map<String, Object>>) companions.get("catch");
            var finallySteps = (List<Map<String, Object>>) companions.get("finally");
            String childPrefix = stepKey + ".";
            int offset = 0;
            var resolvedTry = resolve(trySteps, registry, depth + 1, stepPath + " → try", seenNames, annotations, childPrefix, offset);
            offset += trySteps.size();
            var resolvedCatch = catchSteps != null ? resolve(catchSteps, registry, depth + 1, stepPath + " → catch", seenNames, annotations, childPrefix, offset) : null;
            offset += catchSteps != null ? catchSteps.size() : 0;
            var resolvedFinally = finallySteps != null ? resolve(finallySteps, registry, depth + 1, stepPath + " → finally", seenNames, annotations, childPrefix, offset) : null;
            return new ResolvedStep.TryCatchFinallyStep(
                    stepName, resolvedTry, resolvedCatch, resolvedFinally, decorators);
        }
        if ("select".equals(structuralType)) {
            var branchMaps = (List<Map<String, Object>>) structuralValue;
            return new ResolvedStep.SelectStep(
                    stepName,
                    resolveSelectBranches(branchMaps, registry, depth, stepPath, seenNames, annotations, stepKey),
                    decorators);
        }
        if ("barrier".equals(structuralType)) {
            @SuppressWarnings("unchecked")
            var barrierMap = (Map<String, Object>) structuralValue;
            @SuppressWarnings("unchecked")
            var awaitList = (List<String>) barrierMap.get("await");
            if (awaitList == null || awaitList.isEmpty()) {
                throw new IllegalArgumentException(
                        stepPath + ": barrier 'await' must be a non-empty list of step names");
            }
            var timeoutStr = (String) barrierMap.get("timeout");
            return new ResolvedStep.BarrierStep(stepName, awaitList,
                                                io.casehub.yaml.core.orchestration.DurationParser.parseOrNull(timeoutStr), decorators);
        }
        if ("quorum".equals(structuralType)) {
            @SuppressWarnings("unchecked")
            var quorumMap = (Map<String, Object>) structuralValue;
            var required = ((Number) quorumMap.get("required")).intValue();
            @SuppressWarnings("unchecked")
            var ofList = (List<String>) quorumMap.get("of");
            if (ofList == null || ofList.isEmpty()) {
                throw new IllegalArgumentException(
                        stepPath + ": quorum 'of' must be a non-empty list of step names");
            }
            var timeoutStr = (String) quorumMap.get("timeout");
            return new ResolvedStep.QuorumStep(stepName, required, ofList,
                                               io.casehub.yaml.core.orchestration.DurationParser.parseOrNull(timeoutStr), decorators);
        }
        if ("race".equals(structuralType)) {
            @SuppressWarnings("unchecked")
            var branches = (List<Map<String, Object>>) structuralValue;
            var resolvedBranches = resolve(branches, registry, depth + 1, stepPath + " → race", seenNames, annotations, stepKey + ".", 0);
            return new ResolvedStep.RaceStep(stepName, resolvedBranches, decorators);
        }
        if ("pause".equals(structuralType)) {
            return new ResolvedStep.ControlStep(stepName, String.valueOf(structuralValue), false, decorators);
        }
        if ("resume".equals(structuralType)) {
            return new ResolvedStep.ControlStep(stepName, String.valueOf(structuralValue), true, decorators);
        }
        if (structuralType != null && PROMOTABLE_DECORATORS.contains(structuralType)) {
            if (structuralValue instanceof List) {
                @SuppressWarnings("unchecked")
                var stepList = (List<Map<String, Object>>) structuralValue;
                var resolved = resolve(stepList, registry, depth + 1,
                                       stepPath + " → " + structuralType, seenNames, annotations, stepKey + ".", 0);
                decorators.put(structuralType, true);
                return new ResolvedStep.BlockStep(stepName, resolved, decorators);
            }
            @SuppressWarnings("unchecked")
            var paramMap = (Map<String, Object>) structuralValue;
            if (paramMap.size() != 1) {
                throw new IllegalArgumentException(
                        stepPath + ": structural '" + structuralType
                        + "' must have exactly one key (the parameter), got " + paramMap.size());
            }
            var    entry = paramMap.entrySet().iterator().next();
            String param = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof List)) {
                throw new IllegalArgumentException(
                        stepPath + ": structural '" + structuralType
                        + "' parameter '" + param + "' must map to a steps array");
            }
            @SuppressWarnings("unchecked")
            var stepList = (List<Map<String, Object>>) entry.getValue();
            var resolved = resolve(stepList, registry, depth + 1,
                                   stepPath + " → " + structuralType, seenNames, annotations, stepKey + ".", 0);
            decorators.put(structuralType, param);
            return new ResolvedStep.BlockStep(stepName, resolved, decorators);
        }
        if (matchedAction != null) {
            return new ResolvedStep.PluginStep(stepName, matchedAction, actionParams, decorators);
        }
        if (invokeSpec != null) {
            return new ResolvedStep.InvokeStep(stepName, invokeSpec, decorators);
        }
        if (!decorators.isEmpty()) {
            return new ResolvedStep.BlockStep(stepName, List.of(), decorators);
        }

        throw new IllegalArgumentException(
                stepPath + ": no step type identified. Available actions: "
                + registry.availableActions());
    }

    @SuppressWarnings("unchecked")
    private static List<ResolvedMatchCase> resolveMatchCases(
            List<Map<String, Object>> cases, PluginRegistry registry, int stepIndex, int depth, String path,
            Set<String> seenNames, AnnotationMap annotations, String stepKey) {
        String childPrefix = stepKey + ".";
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
                        resolve(steps, registry, depth + 1, casePath + " → default", seenNames, annotations, childPrefix, i)));
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
                var rest = stripKeys(caseMap, Set.of("pattern", "when", "guard"));
                List<ResolvedStep> caseSteps;
                if (rest.isEmpty()) {
                    caseSteps = List.of();
                } else {
                    String caseKey = childPrefix + i;
                    ResolvedStep caseStep = resolveOne(rest, registry, i, depth + 1, casePath, seenNames, annotations, caseKey);
                    List<io.casehub.yaml.core.annotation.Annotation> caseAnns = annotations.forStep(caseKey);
                    if (!caseAnns.isEmpty()) {
                        caseStep = mergeAnnotationDecorators(caseStep, caseAnns, i);
                        caseStep = withAnnotations(caseStep, caseAnns);
                    }
                    caseSteps = List.of(caseStep);
                }
                result.add(new ResolvedMatchCase(pattern, guard, caseSteps));
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
            List<Map<String, Object>> branches, PluginRegistry registry, int depth, String path,
            Set<String> seenNames, AnnotationMap annotations, String stepKey) {
        String childPrefix = stepKey + ".";
        List<ResolvedStep.SelectBranch> result = new ArrayList<>(branches.size());
        for (int i = 0; i < branches.size(); i++) {
            Map<String, Object>           branchMap  = branches.get(i);
            String                        branchPath = path + " → select[" + i + "]";
            ResolvedStep.SelectBranchType type;
            String                        name;
            if (branchMap.containsKey("subscribe")) {
                type = ResolvedStep.SelectBranchType.SUBSCRIBE;
                Object subSpec = branchMap.get("subscribe");
                if (subSpec instanceof Map<?, ?> subMap) {
                    name = String.valueOf(subMap.get("channel"));
                } else {
                    name = String.valueOf(subSpec);
                }
            } else if (branchMap.containsKey("wait")) {
                type = ResolvedStep.SelectBranchType.WAIT;
                name = String.valueOf(branchMap.get("wait"));
            } else {
                throw new IllegalArgumentException(
                        branchPath + ": select branch must contain 'subscribe' or 'wait'");
            }
            var rest = stripKeys(branchMap, Set.of("subscribe", "wait"));
            List<ResolvedStep> branchSteps;
            if (rest.isEmpty()) {
                branchSteps = List.of();
            } else {
                String branchKey = childPrefix + i;
                ResolvedStep branchStep = resolveOne(rest, registry, 0, depth + 1, branchPath, seenNames, annotations, branchKey);
                List<io.casehub.yaml.core.annotation.Annotation> branchAnns = annotations.forStep(branchKey);
                if (!branchAnns.isEmpty()) {
                    branchStep = mergeAnnotationDecorators(branchStep, branchAnns, i);
                    branchStep = withAnnotations(branchStep, branchAnns);
                }
                branchSteps = List.of(branchStep);
            }
            result.add(new ResolvedStep.SelectBranch(type, name, branchSteps));
        }
        return result;
    }

    private static void validateBarrierQuorumReferences(
            List<ResolvedStep> steps, Set<String> knownNames) {
        for (ResolvedStep step : steps) {
            switch (step) {
                case ResolvedStep.BarrierStep b -> {
                    for (String name : b.awaitSteps()) {
                        if (!knownNames.contains(name)) {
                            throw new IllegalArgumentException(
                                    "barrier '" + b.name() + "' awaits unknown step '" + name + "'");
                        }
                    }
                }
                case ResolvedStep.QuorumStep q -> {
                    for (String name : q.ofSteps()) {
                        if (!knownNames.contains(name)) {
                            throw new IllegalArgumentException(
                                    "quorum '" + q.name() + "' references unknown step '" + name + "'");
                        }
                    }
                }
                case ResolvedStep.BlockStep b -> validateBarrierQuorumReferences(b.steps(), knownNames);
                case ResolvedStep.ParallelStep p -> validateBarrierQuorumReferences(p.steps(), knownNames);
                case ResolvedStep.RaceStep r -> validateBarrierQuorumReferences(r.branches(), knownNames);
                case ResolvedStep.TryCatchFinallyStep t -> {
                    validateBarrierQuorumReferences(t.trySteps(), knownNames);
                    validateBarrierQuorumReferences(t.catchSteps(), knownNames);
                    validateBarrierQuorumReferences(t.finallySteps(), knownNames);
                }
                default -> {}
            }
        }
    }


}
