package io.casehub.yaml.core.foreach;

import io.casehub.yaml.core.condition.Truthiness;
import io.casehub.yaml.core.resolver.VariableResolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ForEachExpander {

    private ForEachExpander() {}

    @SuppressWarnings("unchecked")
    public static <E> ExpansionResult<E> expand(
            Map<String, E> elements,
            Map<String, IterationGroup> iterationGroups,
            VariableResolver resolver,
            ForEachAdapter<E> adapter,
            int maxExpansion) {

        LinkedHashMap<String, E> allElements = new LinkedHashMap<>();
        Set<String> excludedIds = new HashSet<>();
        Map<String, String> elementToGroup = new LinkedHashMap<>();
        Map<String, List<String>> groupValues = new LinkedHashMap<>();

        for (Map.Entry<String, E> entry : elements.entrySet()) {
            String elementId = entry.getKey();
            E element = entry.getValue();
            Object forEach = adapter.getForEach(element);

            if (forEach == null) {
                elementToGroup.put(elementId, null);
                continue;
            }

            List<String> values;
            String groupKey;

            if (forEach instanceof String groupRef) {
                groupKey = groupRef;
                if (!groupValues.containsKey(groupRef)) {
                    IterationGroup group = iterationGroups.get(groupRef);
                    if (group == null) {
                        throw new IllegalArgumentException(
                                "forEach on '" + elementId
                                + "' references unknown iteration group '" + groupRef + "'.");
                    }
                    values = resolveValues(group.inAsList(), resolver, groupRef);
                    groupValues.put(groupRef, values);
                }
                values = groupValues.get(groupRef);
            } else if (forEach instanceof Map<?, ?> inlineMap) {
                groupKey = "__inline__" + elementId;
                List<?> in = (List<?>) inlineMap.get("in");
                if (in == null) { in = List.of(); }
                values = resolveValues(in, resolver, elementId);
                groupValues.put(groupKey, values);
            } else {
                throw new IllegalArgumentException(
                        "Invalid forEach on element '" + elementId
                        + "': expected String (group ref) or Map (inline), got "
                        + forEach.getClass().getSimpleName());
            }

            elementToGroup.put(elementId, groupKey);

            if (values.size() > maxExpansion) {
                throw new IllegalStateException(
                        "forEach template '" + elementId + "' would expand to "
                        + values.size() + " elements (limit: " + maxExpansion + ").");
            }
        }

        for (Map.Entry<String, E> entry : elements.entrySet()) {
            String elementId = entry.getKey();
            E element = entry.getValue();
            String groupKey = elementToGroup.get(elementId);

            if (groupKey == null) {
                String when = adapter.getWhen(element);
                if (when != null) {
                    String resolvedWhen = resolver.resolveString(when, elementId);
                    if (!Truthiness.isTruthy(resolvedWhen)) {
                        excludedIds.add(elementId);
                        continue;
                    }
                }
                allElements.put(elementId, adapter.stamp(element, elementId, resolver));
                continue;
            }

            List<String> values = groupValues.get(groupKey);
            String as = resolveAs(adapter.getForEach(element), iterationGroups);

            for (String value : values) {
                String stampedId = elementId + "." + value;
                VariableResolver eachResolver = resolver.withEachContext(Map.of(as, value));

                String when = adapter.getWhen(element);
                if (when != null) {
                    String resolvedWhen = eachResolver.resolveString(when, stampedId);
                    if (!Truthiness.isTruthy(resolvedWhen)) {
                        excludedIds.add(stampedId);
                        continue;
                    }
                }

                if (allElements.containsKey(stampedId)) {
                    throw new IllegalStateException("Duplicate stamped ID '" + stampedId
                            + "' — forEach values must be unique within each template.");
                }
                allElements.put(stampedId, adapter.stamp(element, stampedId, eachResolver));
            }
        }

        return new ExpansionResult<>(allElements, Set.copyOf(excludedIds));
    }

    private static List<String> resolveValues(List<?> in, VariableResolver resolver,
                                               String context) {
        List<String> values = new ArrayList<>();
        for (Object item : in) {
            String s = item.toString();
            if (s.contains("${")) {
                s = resolver.resolveString(s, "forEach." + context);
            }
            values.add(s);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static String resolveAs(Object forEach,
                                     Map<String, IterationGroup> groups) {
        if (forEach instanceof String groupRef) {
            return groups.get(groupRef).as();
        }
        if (forEach instanceof Map<?, ?> m) {
            return (String) m.get("as");
        }
        throw new IllegalArgumentException("Invalid forEach: " + forEach);
    }
}
