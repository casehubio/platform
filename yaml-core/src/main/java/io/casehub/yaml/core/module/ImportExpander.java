package io.casehub.yaml.core.module;

import io.casehub.yaml.core.condition.Truthiness;
import io.casehub.yaml.core.data.CsvDataSource;
import io.casehub.yaml.core.foreach.ForEachDirective;
import io.casehub.yaml.core.foreach.IterationGroup;
import io.casehub.yaml.core.resolver.ObjectVariableSource;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.VariableSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ImportExpander {

    private ImportExpander() {}

    public static List<YamlImport> expand(
            List<YamlImport> imports,
            Map<String, IterationGroup> iterationGroups,
            Map<String, CsvDataSource> dataSources,
            VariableResolver resolver) {

        List<YamlImport> result = new ArrayList<>();
        Set<String> seenAliases = new HashSet<>();

        for (YamlImport imp : imports) {
            if (imp.forEach() == null) {
                result.add(imp);
                seenAliases.add(imp.as());
                continue;
            }

            ForEachDirective directive = ForEachDirective.parse(imp.forEach());
            String as = resolveAs(directive, iterationGroups);

            if (directive instanceof ForEachDirective.GroupRef ref) {
                CsvDataSource csv = dataSources.get(ref.groupName());
                if (csv != null && !csv.rows().isEmpty()) {
                    expandCsv(imp, csv, as, resolver, result, seenAliases);
                } else {
                    IterationGroup group = iterationGroups.get(ref.groupName());
                    if (group == null) {
                        throw new IllegalArgumentException(
                                "Import '" + imp.as() + "' forEach references unknown group '"
                                + ref.groupName() + "'.");
                    }
                    expandList(imp, resolveValues(group.inAsList(), resolver),
                               as, resolver, result, seenAliases);
                }
            } else if (directive instanceof ForEachDirective.InlineIteration inline) {
                expandList(imp, resolveValues(inline.in(), resolver),
                           as, resolver, result, seenAliases);
            }
        }

        return List.copyOf(result);
    }

    private static void expandList(YamlImport imp, List<String> values, String as,
                                    VariableResolver resolver,
                                    List<YamlImport> result, Set<String> seenAliases) {
        for (String value : values) {
            validateValue(value, imp.as());
            String stampedAlias = imp.as() + "-" + value;
            validateUniqueAlias(stampedAlias, seenAliases);

            VariableResolver eachResolver = resolver.withScope("each",
                    VariableSource.forEachContext(Map.of(as, value), null));

            if (imp.when() != null) {
                String resolvedWhen = eachResolver.resolveString(imp.when(), stampedAlias);
                if (!Truthiness.isTruthy(resolvedWhen)) continue;
            }

            Map<String, String> resolvedParams = resolveParams(
                    imp.parameters(), eachResolver, stampedAlias);
            result.add(new YamlImport(imp.module(), stampedAlias,
                    null, resolvedParams, null, imp.loop()));
        }
    }

    private static void expandCsv(YamlImport imp, CsvDataSource csv, String as,
                                    VariableResolver resolver,
                                    List<YamlImport> result, Set<String> seenAliases) {
        String firstCol = csv.columns().get(0).name();
        for (int i = 0; i < csv.rows().size(); i++) {
            Map<String, Object> row = csv.rows().get(i);
            String rowKey = String.valueOf(row.get(firstCol));
            validateValue(rowKey, imp.as());
            String stampedAlias = imp.as() + "-" + rowKey;
            validateUniqueAlias(stampedAlias, seenAliases);

            VariableResolver eachResolver = resolver.withScope("each",
                    VariableSource.forEachContext(
                            Map.of(as, rowKey, "index", String.valueOf(i)),
                            Map.of(as, row)))
                    .withObjectScope("each", ObjectVariableSource.drillOnly(
                            name -> name.equals(as) ? row : null));

            if (imp.when() != null) {
                String resolvedWhen = eachResolver.resolveString(imp.when(), stampedAlias);
                if (!Truthiness.isTruthy(resolvedWhen)) continue;
            }

            Map<String, String> resolvedParams = resolveParams(
                    imp.parameters(), eachResolver, stampedAlias);
            result.add(new YamlImport(imp.module(), stampedAlias,
                    null, resolvedParams, null, imp.loop()));
        }
    }

    private static Map<String, String> resolveParams(Map<String, String> params,
                                                       VariableResolver resolver,
                                                       String context) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            resolved.put(entry.getKey(),
                    resolver.resolveString(entry.getValue(), context));
        }
        return Map.copyOf(resolved);
    }

    private static List<String> resolveValues(List<?> in, VariableResolver resolver) {
        List<String> values = new ArrayList<>();
        for (Object item : in) {
            String s = item.toString();
            if (s.contains("${")) {
                s = resolver.resolveString(s, "forEach.import");
            }
            values.add(s);
        }
        return values;
    }

    private static String resolveAs(ForEachDirective directive,
                                     Map<String, IterationGroup> groups) {
        return switch (directive) {
            case ForEachDirective.GroupRef ref -> {
                if (ref.as() != null) yield ref.as();
                IterationGroup group = groups.get(ref.groupName());
                yield group != null ? group.as() : ref.groupName();
            }
            case ForEachDirective.InlineIteration inline -> inline.as();
        };
    }

    private static void validateValue(String value, String importAlias) {
        if (value.contains(".")) {
            throw new IllegalArgumentException(
                    "Import '" + importAlias + "' forEach value '" + value
                    + "' contains '.', which is reserved as the ID separator.");
        }
    }

    private static void validateUniqueAlias(String alias, Set<String> seen) {
        if (!seen.add(alias)) {
            throw new IllegalArgumentException(
                    "Duplicate stamped import alias '" + alias
                    + "'. forEach values must be unique.");
        }
    }
}
