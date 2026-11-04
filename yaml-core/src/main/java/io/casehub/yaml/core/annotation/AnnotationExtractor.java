package io.casehub.yaml.core.annotation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class AnnotationExtractor {

    private AnnotationExtractor() {}

    public static AnnotationMap extract(String yamlText) {
        if (yamlText == null || yamlText.isEmpty()) {return AnnotationMap.empty();}

        String[]                      lines               = yamlText.split("\n", -1);
        List<Annotation>              documentAnnotations = new ArrayList<>();
        Map<String, List<Annotation>> stepAnnotations     = new LinkedHashMap<>();
        List<Annotation>              pendingAnnotations  = new ArrayList<>();
        boolean                       inSteps             = false;
        TreeMap<Integer, Integer>     indentCounters      = new TreeMap<>();

        for (String line : lines) {
            String trimmed = line.stripLeading();

            Annotation ann = AnnotationParser.parse(line);
            if (ann != null) {
                pendingAnnotations.add(ann);
                continue;
            }

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {continue;}

            if (!inSteps && trimmed.startsWith("steps:")) {
                flushToDocument(documentAnnotations, pendingAnnotations);
                inSteps = true;
                continue;
            }

            if (inSteps && trimmed.startsWith("- ")) {
                int indent = line.length() - trimmed.length();

                Integer previousAtThisLevel = indentCounters.get(indent);
                indentCounters.tailMap(indent, true).clear();
                int counter = (previousAtThisLevel != null) ? previousAtThisLevel + 1 : 0;
                indentCounters.put(indent, counter);

                String stepKey = buildStepKey(indentCounters);

                if (!pendingAnnotations.isEmpty()) {
                    validateNoDuplicates(pendingAnnotations, "step " + stepKey);
                    stepAnnotations.put(stepKey, List.copyOf(pendingAnnotations));
                    pendingAnnotations.clear();
                }
                continue;
            }

            if (!inSteps && !pendingAnnotations.isEmpty()) {
                flushToDocument(documentAnnotations, pendingAnnotations);
            }
        }

        if (!pendingAnnotations.isEmpty()) {
            flushToDocument(documentAnnotations, pendingAnnotations);
        }

        return new AnnotationMap(documentAnnotations, stepAnnotations);
    }


    private static String buildStepKey(TreeMap<Integer, Integer> indentCounters) {
        StringBuilder sb = new StringBuilder();
        for (int counter : indentCounters.values()) {
            if (!sb.isEmpty()) {sb.append('.');}
            sb.append(counter);
        }
        return sb.toString();
    }

    private static void flushToDocument(List<Annotation> target, List<Annotation> pending) {
        validateNoDuplicates(pending, "document");
        target.addAll(pending);
        pending.clear();
    }

    private static void validateNoDuplicates(List<Annotation> annotations, String context) {
        Set<String> seen = new HashSet<>();
        for (Annotation ann : annotations) {
            if (!seen.add(ann.name())) {
                throw new IllegalArgumentException(
                    "Duplicate annotation @" + ann.name() + " on " + context);
            }
        }
    }
}
