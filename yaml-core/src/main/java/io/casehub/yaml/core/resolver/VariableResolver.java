package io.casehub.yaml.core.resolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, VariableSource> prefixSources;
    private final Set<String> deferredPrefixes;
    private final Map<String, String> eachContext;
    private final Map<String, Map<String, Object>> eachRowContext;
    private final DeferredPrefixHandler deferredPrefixHandler;

    public VariableResolver(Map<String, VariableSource> prefixSources,
                            Set<String> deferredPrefixes) {
        this.prefixSources = Map.copyOf(prefixSources);
        this.deferredPrefixes = Set.copyOf(deferredPrefixes);
        this.eachContext = null;
        this.eachRowContext = null;
        this.deferredPrefixHandler = null;
    }

    private VariableResolver(Map<String, VariableSource> prefixSources,
                             Set<String> deferredPrefixes,
                             Map<String, String> eachContext,
                             Map<String, Map<String, Object>> eachRowContext,
                             DeferredPrefixHandler deferredPrefixHandler) {
        this.prefixSources = prefixSources;
        this.deferredPrefixes = deferredPrefixes;
        this.eachContext = eachContext;
        this.eachRowContext = eachRowContext;
        this.deferredPrefixHandler = deferredPrefixHandler;
    }

    public VariableResolver withEachContext(Map<String, String> eachContext) {
        return new VariableResolver(prefixSources, deferredPrefixes, eachContext, eachRowContext, deferredPrefixHandler);
    }

    public VariableResolver withEachRowContext(Map<String, Map<String, Object>> rowContext) {
        return new VariableResolver(prefixSources, deferredPrefixes, eachContext, rowContext, deferredPrefixHandler);
    }

    public VariableResolver withScope(String prefix, VariableSource source) {
        var newSources = new LinkedHashMap<>(prefixSources);
        newSources.put(prefix, source);
        return new VariableResolver(Map.copyOf(newSources), deferredPrefixes, eachContext, eachRowContext, deferredPrefixHandler);
    }

    public VariableResolver withDeferredPrefixHandler(DeferredPrefixHandler handler) {
        return new VariableResolver(prefixSources, deferredPrefixes, eachContext, eachRowContext, handler);
    }

    public VariableSource sourceFor(String prefix) {
        return prefixSources.get(prefix);
    }

    public VariableResolver withChainedScope(String prefix, VariableSource source) {
        VariableSource existing = prefixSources.get(prefix);
        VariableSource chained = existing != null
                                 ? VariableSource.chain(source, existing) : source;
        return withScope(prefix, chained);
    }


    public Object resolve(Object value) {
        if (value instanceof String s) {
            return s.contains("${") ? resolveString(s, "") : s;
        }
        if (value instanceof Map<?, ?> map) { return resolveMap(map, ""); }
        if (value instanceof List<?> list) { return resolveList(list, ""); }
        return value;
    }

    public String resolveString(String template, String elementContext) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String resolved = lookupVariable(key, elementContext);
            if (resolved == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public Map<String, Object> resolveMap(Map<?, ?> input, String elementContext) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            String key = entry.getKey().toString();
            Object val = entry.getValue();
            if (val instanceof String s && s.contains("${")) {
                result.put(key, resolveString(s, elementContext));
            } else if (val instanceof Map<?, ?> nested) {
                result.put(key, resolveMap(nested, elementContext));
            } else if (val instanceof List<?> list) {
                result.put(key, resolveList(list, elementContext));
            } else {
                result.put(key, val);
            }
        }
        return result;
    }

    public List<?> resolveList(List<?> input, String elementContext) {
        return input.stream()
                .map(item -> {
                    if (item instanceof String s && s.contains("${")) {
                        return resolveString(s, elementContext);
                    }
                    if (item instanceof Map<?, ?> map) {
                        return resolveMap(map, elementContext);
                    }
                    return item;
                })
                .toList();
    }

    private String lookupVariable(String key, String elementContext) {
        int dot = key.indexOf('.');
        if (dot < 0) {
            throw new UnresolvedVariableException(key, elementContext,
                    "Bare variable references are not supported. "
                    + "Use ${var." + key + "} instead of ${" + key + "}. "
                    + "Available prefixes: " + availablePrefixes() + ".");
        }

        String prefix = key.substring(0, dot);
        String nameWithDefault = key.substring(dot + 1);

        String name;
        String defaultValue;
        int defaultSep = nameWithDefault.indexOf(":-");
        if (defaultSep >= 0) {
            name = nameWithDefault.substring(0, defaultSep);
            defaultValue = nameWithDefault.substring(defaultSep + 2);
        } else {
            name = nameWithDefault;
            defaultValue = null;
        }

        if (deferredPrefixes.contains(prefix)) {
            if (deferredPrefixHandler != null) {
                deferredPrefixHandler.onDeferred(prefix, key, elementContext);
            }
            return null;
        }

        if ("each".equals(prefix)) {
            return resolveEach(name, key, elementContext);
        }

        VariableSource source = prefixSources.get(prefix);
        if (source != null) {
            String value = source.resolve(name);
            if (value != null) return value;
            if (defaultValue != null) return defaultValue;
            throw new UnresolvedVariableException(key, elementContext,
                    "Variable '" + name + "' not found in prefix '" + prefix + "'.");
        }

        throw new UnresolvedVariableException(key, elementContext,
                "Unknown prefix '" + prefix + "'. "
                + "Available prefixes: " + availablePrefixes() + ".");
    }

    private String resolveEach(String name, String key, String elementContext) {
        int dot = name.indexOf('.');
        if (dot >= 0) {
            String rowName = name.substring(0, dot);
            String fieldPath = name.substring(dot + 1);
            if (eachRowContext != null) {
                Map<String, Object> row = eachRowContext.get(rowName);
                if (row != null) {
                    Object value = drillField(row, fieldPath);
                    if (value != null) return value.toString();
                    throw new UnresolvedVariableException(key, elementContext,
                            "Field '" + fieldPath + "' not found in row '" + rowName
                            + "'. Available fields: " + row.keySet());
                }
            }
        }

        if (eachContext != null) {
            String value = eachContext.get(name);
            if (value != null) return value;
            if (eachRowContext != null) {
                Map<String, Object> row = eachRowContext.get(name);
                if (row != null) {
                    throw new UnresolvedVariableException(key, elementContext,
                            "'" + name + "' is a row — use field access like ${each."
                            + name + ".fieldName}. Available fields: " + row.keySet());
                }
            }
            throw new UnresolvedVariableException(key, elementContext,
                    "Unknown forEach variable '" + name + "'. Available: " + eachContext.keySet());
        }

        if (eachRowContext != null) {
            Map<String, Object> row = eachRowContext.get(name);
            if (row != null) {
                throw new UnresolvedVariableException(key, elementContext,
                        "'" + name + "' is a row — use field access like ${each."
                        + name + ".fieldName}. Available fields: " + row.keySet());
            }
        }

        throw new UnresolvedVariableException(key, elementContext,
                "${each.*} references are resolved during forEach expansion. "
                + "No forEach context is active.");
    }

    @SuppressWarnings("unchecked")
    private static Object drillField(Map<String, Object> map, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?> m) { current = m.get(part); }
            else { return null; }
        }
        return current;
    }

    private String availablePrefixes() {
        var all = new TreeSet<>(prefixSources.keySet());
        all.add("each");
        all.addAll(deferredPrefixes);
        return all.toString();
    }
}
