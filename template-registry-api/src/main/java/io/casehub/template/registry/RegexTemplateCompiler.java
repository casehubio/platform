package io.casehub.template.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexTemplateCompiler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern VALID_VAR_NAME = Pattern.compile(
        "[a-zA-Z]([a-zA-Z0-9]*[.\\-][a-zA-Z0-9]+)*[a-zA-Z0-9]*");

    private RegexTemplateCompiler() {}

    public static CompiledTemplate compile(String template) {
        Objects.requireNonNull(template, "template");

        List<Segment> segments = parseSegments(template);
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, String> sanitizedToOriginal = new HashMap<>();
        Map<String, String> groupNameToVarName = new LinkedHashMap<>();

        StringBuilder regex = new StringBuilder();
        for (Segment segment : segments) {
            if (segment.optional) {
                regex.append("(?:");
            }
            appendSegmentRegex(regex, segment.text, names, seen,
                sanitizedToOriginal, groupNameToVarName, segment.lastVarIsLast);
            if (segment.optional) {
                regex.append(")?");
            }
        }

        Pattern compiled = Pattern.compile(regex.toString());
        return new CompiledTemplate(template, compiled,
            Collections.unmodifiableList(names),
            Collections.unmodifiableMap(groupNameToVarName));
    }

    private static void appendSegmentRegex(StringBuilder regex, String text,
                                            List<String> names, Set<String> seen,
                                            Map<String, String> sanitizedToOriginal,
                                            Map<String, String> groupNameToVarName,
                                            boolean lastVarIsGlobal) {
        Matcher varMatcher = VAR_PATTERN.matcher(text);
        List<String> segNames = new ArrayList<>();
        List<String> segGroupNames = new ArrayList<>();
        List<int[]> positions = new ArrayList<>();

        while (varMatcher.find()) {
            String name = varMatcher.group(1);
            if (!VALID_VAR_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                    "Invalid variable name '" + name + "'");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException(
                    "Duplicate variable name: " + name);
            }
            segNames.add(name);
            String groupName = sanitizeGroupName(name);
            String previous = sanitizedToOriginal.put(groupName, name);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "Sanitized group name collision: '" + name + "' and '"
                    + previous + "' both resolve to '" + groupName + "'");
            }
            segGroupNames.add(groupName);
            positions.add(new int[]{varMatcher.start(), varMatcher.end()});
        }

        int lastEnd = 0;
        for (int i = 0; i < segNames.size(); i++) {
            int[] pos = positions.get(i);
            if (pos[0] > lastEnd) {
                regex.append(Pattern.quote(text.substring(lastEnd, pos[0])));
            }
            boolean isLast = (i == segNames.size() - 1);
            String quantifier = (isLast && lastVarIsGlobal) ? ".+" : ".+?";
            regex.append("(?<").append(segGroupNames.get(i)).append(">")
                .append(quantifier).append(")");
            groupNameToVarName.put(segGroupNames.get(i), segNames.get(i));
            lastEnd = pos[1];
        }

        if (lastEnd < text.length()) {
            regex.append(Pattern.quote(text.substring(lastEnd)));
        }

        names.addAll(segNames);
    }

    static String sanitizeGroupName(String name) {
        if (name.indexOf('.') < 0 && name.indexOf('-') < 0) {
            return name;
        }
        return name.replace(".", "0DOT0").replace("-", "0HYPHEN0");
    }

    static List<Segment> parseSegments(String template) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inOptional = false;
        int i = 0;

        while (i < template.length()) {
            char c = template.charAt(i);

            if (c == '\\' && i + 1 < template.length()) {
                char next = template.charAt(i + 1);
                if (next == '[' || next == ']') {
                    current.append(next);
                    i += 2;
                    continue;
                }
            }

            if (c == '[') {
                if (inOptional) {
                    throw new IllegalArgumentException(
                        "Nested optional segments are not supported");
                }
                if (current.length() > 0) {
                    segments.add(new Segment(current.toString(), false, false));
                    current.setLength(0);
                }
                inOptional = true;
                i++;
            } else if (c == ']') {
                if (!inOptional) {
                    throw new IllegalArgumentException(
                        "Unexpected ']' without matching '['");
                }
                segments.add(new Segment(current.toString(), true, false));
                current.setLength(0);
                inOptional = false;
                i++;
            } else {
                current.append(c);
                i++;
            }
        }

        if (inOptional) {
            throw new IllegalArgumentException("Unclosed optional segment");
        }

        if (current.length() > 0) {
            segments.add(new Segment(current.toString(), false, false));
        }

        if (!segments.isEmpty()) {
            Segment last = segments.get(segments.size() - 1);
            segments.set(segments.size() - 1,
                new Segment(last.text, last.optional, true));
        }

        return segments;
    }

    record Segment(String text, boolean optional, boolean lastVarIsLast) {}
}
