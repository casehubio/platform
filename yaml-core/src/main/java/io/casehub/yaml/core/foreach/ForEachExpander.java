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
                VariableResolver eachResolver = resolver.withScope("each", io.casehub.yaml.core.resolver.VariableSource.forEachContext(Map.of(as, value), null));

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

    public static <E> ExpansionResult<E> expand(
            Map<String, E> elements,
            Map<String, IterationGroup> iterationGroups,
            Map<String, io.casehub.yaml.core.data.CsvDataSource> dataSources,
            VariableResolver resolver,
            ForEachAdapter<E> adapter,
            int maxExpansion) {

        LinkedHashMap<String, E>  allElements    = new LinkedHashMap<>();
        Set<String>               excludedIds    = new HashSet<>();
        Map<String, String>       elementToGroup = new LinkedHashMap<>();
        Map<String, List<String>> groupValues    = new LinkedHashMap<>();
        Set<String>               csvGroups      = new HashSet<>();

        for (Map.Entry<String, E> entry : elements.entrySet()) {
            String           elementId = entry.getKey();
            E                element   = entry.getValue();
            ForEachDirective forEach   = adapter.getForEach(element);

            if (forEach == null) {
                elementToGroup.put(elementId, null);
                continue;
            }

            String groupKey;

            if (forEach instanceof ForEachDirective.GroupRef ref) {
                groupKey = ref.groupName();
                if (!groupValues.containsKey(groupKey)) {
                    io.casehub.yaml.core.data.CsvDataSource csv = dataSources.get(groupKey);
                    if (csv != null && !csv.rows().isEmpty()) {
                        csvGroups.add(groupKey);
                        String firstCol = csv.columns().get(0).name();
                        List<String> values = csv.rows().stream()
                                                 .map(row -> String.valueOf(row.get(firstCol)))
                                                 .toList();
                        groupValues.put(groupKey, values);
                    } else {
                        IterationGroup group = iterationGroups.get(groupKey);
                        if (group == null) {
                            throw new IllegalArgumentException(
                                    "forEach on '" + elementId
                                    + "' references unknown group or data source '" + groupKey + "'.");
                        }
                        List<String> values = resolveValuesStatic(group.inAsList(), resolver, groupKey);
                        groupValues.put(groupKey, values);
                    }
                }
            } else if (forEach instanceof ForEachDirective.InlineIteration inline) {
                groupKey = "__inline__" + elementId;
                List<String> values = resolveValuesStatic(inline.in(), resolver, elementId);
                groupValues.put(groupKey, values);
            } else {
                throw new IllegalArgumentException("Unexpected ForEachDirective type: " + forEach);
            }

            elementToGroup.put(elementId, groupKey);

            List<String> values = groupValues.get(groupKey);
            if (values.size() > maxExpansion) {
                throw new IllegalStateException(
                        "forEach template '" + elementId + "' would expand to "
                        + values.size() + " elements (limit: " + maxExpansion + ").");
            }
        }

        for (Map.Entry<String, E> entry : elements.entrySet()) {
            String elementId = entry.getKey();
            E      element   = entry.getValue();
            String groupKey  = elementToGroup.get(elementId);

            if (groupKey == null) {
                String when = adapter.getWhen(element);
                if (when != null) {
                    String resolvedWhen = resolver.resolveString(when, elementId);
                    if (!io.casehub.yaml.core.condition.Truthiness.isTruthy(resolvedWhen)) {
                        excludedIds.add(elementId);
                        continue;
                    }
                }
                allElements.put(elementId, adapter.stamp(element, elementId, resolver));
                continue;
            }

            List<String> values = groupValues.get(groupKey);
            String       as     = resolveAsForGroup(adapter.getForEach(element), iterationGroups);
            boolean      isCsv  = csvGroups.contains(groupKey);

            if (isCsv) {
                io.casehub.yaml.core.data.CsvDataSource csv = dataSources.get(groupKey);
                for (int i = 0; i < csv.rows().size(); i++) {
                    Map<String, Object> row       = csv.rows().get(i);
                    String              rowKey    = values.get(i);
                    String              stampedId = elementId + "." + rowKey;

                    VariableResolver rowResolver = resolver.withScope("each",
                            io.casehub.yaml.core.resolver.VariableSource.forEachContext(
                                    Map.of(as, rowKey, "index", String.valueOf(i)),
                                    Map.of(as, row)));

                    String when = adapter.getWhen(element);
                    if (when != null) {
                        String resolvedWhen = rowResolver.resolveString(when, stampedId);
                        if (!io.casehub.yaml.core.condition.Truthiness.isTruthy(resolvedWhen)) {
                            excludedIds.add(stampedId);
                            continue;
                        }
                    }

                    if (allElements.containsKey(stampedId)) {
                        throw new IllegalStateException("Duplicate stamped ID '" + stampedId
                                                        + "' — forEach values must be unique within each template.");
                    }
                    allElements.put(stampedId, adapter.stamp(element, stampedId, rowResolver));
                }
            } else {
                for (String value : values) {
                    String           stampedId    = elementId + "." + value;
                    VariableResolver eachResolver = resolver.withScope("each", io.casehub.yaml.core.resolver.VariableSource.forEachContext(Map.of(as, value), null));

                    String when = adapter.getWhen(element);
                    if (when != null) {
                        String resolvedWhen = eachResolver.resolveString(when, stampedId);
                        if (!io.casehub.yaml.core.condition.Truthiness.isTruthy(resolvedWhen)) {
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
        }

        for (Map.Entry<String, E> refEntry : new ArrayList<>(allElements.entrySet())) {
            String                         stampedId = refEntry.getKey();
            E                              element   = refEntry.getValue();
            List<ForEachAdapter.Reference> refs      = adapter.getReferences(element);
            if (refs.isEmpty()) {continue;}

            String origId      = originalIdStatic(stampedId);
            String sourceGroup = elementToGroup.get(origId);
            String sourceValue = extractValueStatic(stampedId);

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




    private static List<String> resolveValuesStatic(List<?> in, VariableResolver resolver, String context) {
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

    private static String resolveAsForGroup(ForEachDirective forEach,
                                            Map<String, IterationGroup> groups) {
        return switch (forEach) {
            case ForEachDirective.GroupRef ref -> {
                if (ref.as() != null) {yield ref.as();}
                IterationGroup group = groups.get(ref.groupName());
                yield group != null ? group.as() : ref.groupName();
            }
            case ForEachDirective.InlineIteration inline -> inline.as();
        };
    }

    private static String originalIdStatic(String stampedId) {
        int dot = stampedId.lastIndexOf('.');
        return dot >= 0 ? stampedId.substring(0, dot) : stampedId;
    }

    private static String extractValueStatic(String stampedId) {
        int dot = stampedId.lastIndexOf('.');
        return dot >= 0 ? stampedId.substring(dot + 1) : null;
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
            case ForEachDirective.GroupRef ref -> {
                if (ref.as() != null) {yield ref.as();}
                yield groups.get(ref.groupName()).as();
            }
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
