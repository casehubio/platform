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
        return expand(elements, iterationGroups, resolver, adapter, maxExpansion, null);
    }

    @SuppressWarnings("unchecked")
    public static <E> ExpansionResult<E> expand(
            Map<String, E> elements,
            Map<String, IterationGroup> iterationGroups,
            VariableResolver resolver,
            ForEachAdapter<E> adapter,
            int maxExpansion,
            IterationValueExpander valueExpander) {

        LinkedHashMap<String, E> allElements = new LinkedHashMap<>();
        Set<String> excludedIds = new HashSet<>();
        Map<String, String> elementToGroup = new LinkedHashMap<>();
        Map<String, List<String>> groupValues = new LinkedHashMap<>();

        for (Map.Entry<String, E> entry : elements.entrySet()) {
            String elementId = entry.getKey();
            E element = entry.getValue();
            ForEachDirective forEach = adapter.getForEach(element);

            if (forEach == null) {
                elementToGroup.put(elementId, null);
                continue;
            }

            List<String> values;
            String groupKey;

            if (forEach instanceof ForEachDirective.GroupRef ref) {
                groupKey = ref.groupName();
                if (!groupValues.containsKey(ref.groupName())) {
                    IterationGroup group = iterationGroups.get(ref.groupName());
                    if (group == null) {
                        throw new IllegalArgumentException(
                                "forEach on '" + elementId
                                + "' references unknown iteration group '" + ref.groupName() + "'.");
                    }
                    values = resolveValues(group.inAsList(), resolver, ref.groupName(), valueExpander);
                    groupValues.put(ref.groupName(), values);
                }
                values = groupValues.get(ref.groupName());
            } else if (forEach instanceof ForEachDirective.InlineIteration inline) {
                groupKey = "__inline__" + elementId;
                values = resolveValues(inline.in(), resolver, elementId, valueExpander);
                groupValues.put(groupKey, values);
            } else {
                throw new IllegalArgumentException("Unexpected ForEachDirective type: " + forEach);
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

        for (Map.Entry<String, E> refEntry : new ArrayList<>(allElements.entrySet())) {
            String stampedId = refEntry.getKey();
            E element = refEntry.getValue();
            List<ForEachAdapter.Reference> refs = adapter.getReferences(element);
            if (refs.isEmpty()) continue;

            String origId = originalId(stampedId);
            String sourceGroup = elementToGroup.get(origId);
            String sourceValue = extractValue(stampedId);

            List<ForEachAdapter.Reference> rewritten = new ArrayList<>();
            for (ForEachAdapter.Reference ref : refs) {
                String targetGroup = elementToGroup.get(ref.targetId());

                if (targetGroup == null) {
                    rewritten.add(ref);
                } else if (targetGroup.equals(sourceGroup) && sourceValue != null) {
                    rewritten.add(new ForEachAdapter.Reference(
                            ref.targetId() + "." + sourceValue, ref.optional()));
                } else if (ref.optional()) {
                    // skip optional cross-group ref
                } else {
                    throw new IllegalStateException("Element '" + stampedId
                            + "' references forEach element '" + ref.targetId()
                            + "' in a different group.");
                }
            }

            for (ForEachAdapter.Reference ref : rewritten) {
                if (excludedIds.contains(ref.targetId()) && !ref.optional()) {
                    throw new IllegalStateException("Element '" + stampedId
                            + "' references excluded element '" + ref.targetId() + "'.");
                }
            }

            allElements.put(stampedId, adapter.withReferences(element, rewritten));
        }

        return new ExpansionResult<>(allElements, Set.copyOf(excludedIds));
    }

    private static List<String> resolveValues(List<?> in, VariableResolver resolver,
                                               String context,
                                               IterationValueExpander valueExpander) {
        List<String> values = new ArrayList<>();
        for (Object item : in) {
            String s = item.toString();
            if (s.contains("${")) {
                s = resolver.resolveString(s, "forEach." + context);
            }
            if (valueExpander != null) {
                try {
                    values.addAll(valueExpander.expand(s, context));
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "IterationValueExpander failed for group '" + context
                            + "': resolved value '" + s + "'", e);
                }
            } else {
                values.add(s);
            }
        }
        return values;
    }

    private static String resolveAs(ForEachDirective forEach,
                                    Map<String, IterationGroup> groups) {
        return switch (forEach) {
            case ForEachDirective.GroupRef ref -> groups.get(ref.groupName()).as();
            case ForEachDirective.InlineIteration inline -> inline.as();
        };
    }

    private static String originalId(String stampedId) {
        int dot = stampedId.lastIndexOf('.');
        return dot >= 0 ? stampedId.substring(0, dot) : stampedId;
    }

    private static String extractValue(String stampedId) {
        int dot = stampedId.lastIndexOf('.');
        return dot >= 0 ? stampedId.substring(dot + 1) : null;
    }

}
