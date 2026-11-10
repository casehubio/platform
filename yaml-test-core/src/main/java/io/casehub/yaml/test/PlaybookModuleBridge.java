package io.casehub.yaml.test;

import io.casehub.yaml.core.module.ModuleBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlaybookModuleBridge implements ModuleBridge<Map<String, Object>> {

    static final String EXPANDED_STEP_SECTIONS_KEY = "\0__expanded_step_sections__";

    private Map<String, Object> originalContent;

    @Override
    public Map<String, Map<String, Object>> toSections(Map<String, Object> content) {
        this.originalContent = content;
        return Map.of("steps", Map.of());
    }

    @Override
    public Map<String, Object> fromSections(Map<String, Map<String, Object>> sections) {
        Map<String, Object> result = new LinkedHashMap<>(originalContent);
        result.put(EXPANDED_STEP_SECTIONS_KEY, sections.getOrDefault("steps", Map.of()));
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> expandUseSteps(Map<String, Object> content,
                                                     Map<String, Object> expandedStepSections) {
        var steps = (List<Map<String, Object>>) content.get("steps");
        if (steps == null || steps.isEmpty()) {
            return content;
        }

        List<Map<String, Object>> expandedSteps = expandStepList(steps, expandedStepSections);

        Map<String, Object> result = new LinkedHashMap<>(content);
        result.put("steps", expandedSteps);
        result.remove(EXPANDED_STEP_SECTIONS_KEY);
        return result;
    }

    private static final Set<String> BRANCH_KEYS = Set.of(
            "then", "else", "catch", "finally", "block", "parallel", "try", "race");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> expandStepList(
            List<Map<String, Object>> steps, Map<String, Object> expandedStepSections) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> step : steps) {
            if (step.containsKey("use")) {
                String ref = String.valueOf(step.get("use"));
                int    dot = ref.indexOf('.');
                if (dot < 0) {
                    throw new IllegalArgumentException(
                            "Invalid use: reference '" + ref
                            + "' — expected format 'alias.step-name'");
                }
                Object sequence = expandedStepSections.get(ref);
                if (sequence == null) {
                    String alias    = ref.substring(0, dot);
                    String stepName = ref.substring(dot + 1);
                    Set<String> available = expandedStepSections.keySet().stream()
                                                                .filter(k -> k.startsWith(alias + "."))
                                                                .collect(java.util.stream.Collectors.toSet());
                    throw new UnknownStepSequenceException(alias, stepName, available);
                }
                if (sequence instanceof List<?> stepList) {
                    for (Object s : stepList) {
                        result.add((Map<String, Object>) s);
                    }
                }
            } else {
                result.add(expandBranches(step, expandedStepSections));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> expandBranches(
            Map<String, Object> step, Map<String, Object> expandedStepSections) {
        boolean hasBranch = false;
        for (String key : step.keySet()) {
            if (BRANCH_KEYS.contains(key) && step.get(key) instanceof List) {
                hasBranch = true;
                break;
            }
        }
        if (!hasBranch) {return step;}

        Map<String, Object> expanded = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : step.entrySet()) {
            if (BRANCH_KEYS.contains(entry.getKey()) && entry.getValue() instanceof List<?> branch) {
                expanded.put(entry.getKey(),
                             expandStepList((List<Map<String, Object>>) branch, expandedStepSections));
            } else {
                expanded.put(entry.getKey(), entry.getValue());
            }
        }
        return expanded;
    }

}
