package io.casehub.yaml.core.resolver;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariablePrefixRewriter {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private VariablePrefixRewriter() {}

    public static String rewrite(String input, String defaultPrefix, Set<String> knownPrefixes, Set<String> forEachVars) {
        if (input == null || input.isEmpty()) return input;

        Matcher matcher = VAR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String ref = matcher.group(1);
            String rewritten = rewriteReference(ref, defaultPrefix, knownPrefixes, forEachVars);
            matcher.appendReplacement(sb, "\\${" + Matcher.quoteReplacement(rewritten) + "}");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String rewriteReference(String ref, String defaultPrefix, Set<String> knownPrefixes, Set<String> forEachVars) {
        String name = ref;
        String defaultValue = null;
        int defaultSep = ref.indexOf(":-");
        if (defaultSep >= 0) {
            name = ref.substring(0, defaultSep);
            defaultValue = ref.substring(defaultSep);
        }

        String firstSegment = extractFirstSegment(name);
        String rewrittenName;

        if (knownPrefixes.contains(firstSegment) && !forEachVars.contains(firstSegment)) {
            rewrittenName = name;
        } else if (forEachVars.contains(firstSegment)) {
            rewrittenName = "each." + name;
        } else if (name.contains(".")) {
            rewrittenName = defaultPrefix + "." + name;
        } else {
            rewrittenName = defaultPrefix + "." + name;
        }

        return defaultValue != null ? rewrittenName + defaultValue : rewrittenName;
    }

    private static String extractFirstSegment(String name) {
        int dot = name.indexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }
}
