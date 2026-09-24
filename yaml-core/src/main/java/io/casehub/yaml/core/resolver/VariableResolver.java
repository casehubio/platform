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

    private final Map<String, VariableSource>       prefixSources;
    private final Map<String, ObjectVariableSource> objectPrefixSources;
    private final Set<String>                       deferredPrefixes;
    private final DeferredPrefixHandler             deferredPrefixHandler;

    public VariableResolver(Map<String, VariableSource> prefixSources,
                            Set<String> deferredPrefixes) {
        this.prefixSources         = Map.copyOf(prefixSources);
        this.objectPrefixSources   = Map.of();
        this.deferredPrefixes      = Set.copyOf(deferredPrefixes);
        this.deferredPrefixHandler = null;
    }

    private VariableResolver(Map<String, VariableSource> prefixSources,
                             Map<String, ObjectVariableSource> objectPrefixSources,
                             Set<String> deferredPrefixes,
                             DeferredPrefixHandler deferredPrefixHandler) {
        this.prefixSources         = prefixSources;
        this.objectPrefixSources   = objectPrefixSources;
        this.deferredPrefixes      = deferredPrefixes;
        this.deferredPrefixHandler = deferredPrefixHandler;
    }

    public VariableResolver withScope(String prefix, VariableSource source) {
        var newSources = new LinkedHashMap<>(prefixSources);
        newSources.put(prefix, source);
        return new VariableResolver(Map.copyOf(newSources), objectPrefixSources, deferredPrefixes, deferredPrefixHandler);
    }

    public VariableResolver withDeferredPrefixHandler(DeferredPrefixHandler handler) {
        return new VariableResolver(prefixSources, objectPrefixSources, deferredPrefixes, handler);
    }

    public VariableResolver withObjectScope(String prefix, ObjectVariableSource source) {
        var newObjectSources = new LinkedHashMap<>(objectPrefixSources);
        newObjectSources.put(prefix, source);
        return new VariableResolver(prefixSources, Map.copyOf(newObjectSources), deferredPrefixes, deferredPrefixHandler);
    }


    public static VariableResolver forParams(
            java.util.Map<String, io.casehub.yaml.core.module.YamlModuleParameter> declared,
            java.util.Map<String, String> callerParams,
            java.util.Set<String> deferredPrefixes) {
        java.util.Map<String, String> defaults = new java.util.LinkedHashMap<>();
        for (var entry : declared.entrySet()) {
            if (entry.getValue().defaultValue() != null) {
                defaults.put(entry.getKey(), entry.getValue().defaultValue());
            }
        }
        VariableSource paramSource = VariableSource.chain(callerParams::get, defaults::get);
        return new VariableResolver(
                java.util.Map.of("params", paramSource, "var", paramSource),
                deferredPrefixes);
    }

    public VariableSource sourceFor(String prefix) {
        return prefixSources.get(prefix);
    }

    public VariableResolver withChainedScope(String prefix, VariableSource source) {
        VariableSource existing = prefixSources.get(prefix);
        if (existing == null) {
            ObjectVariableSource objExisting = objectPrefixSources.get(prefix);
            if (objExisting != null) {
                existing = name -> {
                    Object v = objExisting.resolve(name);
                    return v != null ? v.toString() : null;
                };
            }
        }
        VariableSource chained = existing != null
                                 ? VariableSource.chain(source, existing) : source;
        return withScope(prefix, chained);
    }

    public Object resolve(Object value) {
        if (value instanceof String s) {
            if (!s.contains("${")) {return s;}
            if (isSoleReference(s)) {
                Object typed = resolveTyped(s);
                if (typed != null) {return typed;}
            }
            return resolveString(s, "<root>");
        }
        if (value instanceof Map<?, ?> map) {return resolveMap(map, "<root>");}
        if (value instanceof List<?> list) {return resolveList(list, "<root>");}
        return value;
    }

    private static boolean isSoleReference(String s) {
        return s.startsWith("${") && s.endsWith("}") && s.indexOf('}') == s.length() - 1;
    }

    @SuppressWarnings("unchecked")
    private Object resolveTyped(String s) {
        String key = s.substring(2, s.length() - 1);
        int    dot = key.indexOf('.');
        if (dot < 0) {return null;}

        String prefix = key.substring(0, dot);
        String name   = key.substring(dot + 1);

        if (deferredPrefixes.contains(prefix)) {return null;}

        ObjectVariableSource objSource = objectPrefixSources.get(prefix);
        if (objSource == null) {return null;}

        int    nextDot  = name.indexOf('.');
        String rootName = nextDot >= 0 ? name.substring(0, nextDot) : name;
        Object root     = objSource.resolve(rootName);
        if (root == null) {return null;}

        if (nextDot < 0) {
            if (!objSource.allowContainerReturn()
                && (root instanceof Map || root instanceof List)) {
                return null;
            }
            return root;
        }

        String fieldPath = name.substring(nextDot + 1);
        if (root instanceof Map<?, ?> map) {
            return FieldDriller.drill((Map<String, Object>) map, fieldPath);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object drillFields(Map<String, Object> map, String dotPath) {
        return FieldDriller.drill(map, dotPath);
    }


    public String resolveString(String template, String elementContext) {
        Matcher       matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb      = new StringBuilder();
        while (matcher.find()) {
            String key      = matcher.group(1);
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
                if (isSoleReference(s)) {
                    Object typed = resolveTyped(s);
                    if (typed != null) {
                        result.put(key, typed);
                        continue;
                    }
                }
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

    public List<Object> resolveList(List<?> input, String elementContext) {
        return input.stream()
                    .map(item -> {
                        if (item instanceof String s && s.contains("${")) {
                            if (isSoleReference(s)) {
                                Object typed = resolveTyped(s);
                                if (typed != null) {return typed;}
                            }
                            return (Object) resolveString(s, elementContext);
                        }
                        if (item instanceof Map<?, ?> map) {
                            return (Object) resolveMap(map, elementContext);
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

        String prefix          = key.substring(0, dot);
        String nameWithDefault = key.substring(dot + 1);

        String name;
        String defaultValue;
        int    defaultSep = nameWithDefault.indexOf(":-");
        if (defaultSep >= 0) {
            name         = nameWithDefault.substring(0, defaultSep);
            defaultValue = nameWithDefault.substring(defaultSep + 2);
        } else {
            name         = nameWithDefault;
            defaultValue = null;
        }

        if (deferredPrefixes.contains(prefix)) {
            if (deferredPrefixHandler != null) {
                deferredPrefixHandler.onDeferred(prefix, key, elementContext);
            }
            return null;
        }

        VariableSource source = prefixSources.get(prefix);
        if (source != null) {
            String value = source.resolve(name);
            if (value != null) {return value;}
        }

        ObjectVariableSource objSource = objectPrefixSources.get(prefix);
        if (objSource != null) {
            Object value = objSource.resolve(name);
            if (value != null) {return value.toString();}
        }

        if (source != null || objSource != null) {
            if (defaultValue != null) {return defaultValue;}
            throw new UnresolvedVariableException(key, elementContext,
                                                  "Variable '" + name + "' not found in prefix '" + prefix + "'.");
        }

        throw new UnresolvedVariableException(key, elementContext,
                                              "Unknown prefix '" + prefix + "'. "
                                              + "Available prefixes: " + availablePrefixes() + ".");
    }

    private String availablePrefixes() {
        var all = new TreeSet<>(prefixSources.keySet());
        all.addAll(objectPrefixSources.keySet());
        all.addAll(deferredPrefixes);
        return all.toString();
    }
}
