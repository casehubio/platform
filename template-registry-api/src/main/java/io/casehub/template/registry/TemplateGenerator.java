package io.casehub.template.registry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateGenerator {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private TemplateGenerator() {}

    public static String generate(TemplateDefinition definition,
                                   Map<String, String> values) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(values, "values");

        String pattern = definition.patterns().get(0);
        List<RegexTemplateCompiler.Segment> segments =
            RegexTemplateCompiler.parseSegments(pattern);

        StringBuilder result = new StringBuilder();
        for (RegexTemplateCompiler.Segment segment : segments) {
            String expanded = substituteVars(segment.text(), values);

            if (segment.optional()) {
                if (!hasUnresolvedVars(expanded)) {
                    result.append(expanded);
                }
            } else {
                Matcher m = VAR_PATTERN.matcher(expanded);
                if (m.find()) {
                    throw new IllegalArgumentException(
                        "Missing required variable value: " + m.group(1));
                }
                result.append(expanded);
            }
        }

        return result.toString();
    }

    private static String substituteVars(String text, Map<String, String> values) {
        Matcher matcher = VAR_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = values.get(varName);
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean hasUnresolvedVars(String text) {
        return VAR_PATTERN.matcher(text).find();
    }
}
