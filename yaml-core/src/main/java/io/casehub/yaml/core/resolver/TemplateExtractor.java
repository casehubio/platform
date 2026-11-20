package io.casehub.yaml.core.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateExtractor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern VALID_GROUP_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");


    private final String template;
    private final Pattern pattern;
    private final List<String> variableNames;

    private TemplateExtractor(String template, Pattern pattern, List<String> variableNames) {
        this.template = template;
        this.pattern = pattern;
        this.variableNames = variableNames;
    }

    public static TemplateExtractor compile(String template) {
        Objects.requireNonNull(template, "template");

        Matcher               matcher   = VAR_PATTERN.matcher(template);
        List<String>          names     = new ArrayList<>();
        List<int[]>           positions = new ArrayList<>();
        java.util.Set<String> seen      = new java.util.HashSet<>();

        while (matcher.find()) {
            String name = matcher.group(1);
            if (!VALID_GROUP_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "Invalid variable name '" + name + "': must match [a-zA-Z][a-zA-Z0-9]*");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Duplicate variable name: " + name);
            }
            names.add(name);
            positions.add(new int[]{matcher.start(), matcher.end()});
        }

        StringBuilder regex   = new StringBuilder();
        int           lastEnd = 0;

        for (int i = 0; i < names.size(); i++) {
            int[] pos = positions.get(i);
            if (pos[0] > lastEnd) {
                regex.append(Pattern.quote(template.substring(lastEnd, pos[0])));
            }
            boolean isLast     = (i == names.size() - 1);
            String  quantifier = isLast ? ".+" : ".+?";
            regex.append("(?<").append(names.get(i)).append(">").append(quantifier).append(")");
            lastEnd = pos[1];
        }

        if (lastEnd < template.length()) {
            regex.append(Pattern.quote(template.substring(lastEnd)));
        }

        Pattern compiled = Pattern.compile(regex.toString());
        return new TemplateExtractor(template, compiled, Collections.unmodifiableList(names));
    }

    public Optional<Map<String, String>> extract(String input) {
        Objects.requireNonNull(input, "input");

        Matcher matcher = pattern.matcher(input);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String name : variableNames) {
            result.put(name, matcher.group(name));
        }
        return Optional.of(Collections.unmodifiableMap(result));
    }

    public String template() {
        return template;
    }

    public Pattern pattern() {
        return pattern;
    }

    public List<String> variableNames() {
        return variableNames;
    }

}
