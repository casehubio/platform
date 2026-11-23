package io.casehub.extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexMessageMatcher implements MessageMatcher {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern VALID_VAR_NAME = Pattern.compile("[a-zA-Z]([a-zA-Z0-9]*[.\\-][a-zA-Z0-9]+)*[a-zA-Z0-9]*");

    private final List<CompiledType> compiledTypes;

    public RegexMessageMatcher(MessageSchema schema) {
        Objects.requireNonNull(schema, "schema");
        List<CompiledType> types = new ArrayList<>();
        for (MessageType type : schema.types()) {
            List<CompiledTemplate> templates = new ArrayList<>();
            for (String template : type.templates()) {
                templates.add(compileTemplate(template));
            }
            types.add(new CompiledType(type.name(), templates, type.variables()));
        }
        this.compiledTypes = Collections.unmodifiableList(types);
    }

    @Override
    public MessageMatch match(String input) {
        Objects.requireNonNull(input, "input");
        for (CompiledType type : compiledTypes) {
            for (int i = 0; i < type.templates.size(); i++) {
                Optional<Map<String, String>> extracted = type.templates.get(i).extract(input);
                if (extracted.isPresent() && validateConstraints(extracted.get(), type.variables)) {
                    return new MessageMatch.Matched(type.name, i, extracted.get(), 1.0);
                }
            }
        }
        return new MessageMatch.Unmatched(input);
    }

    private boolean validateConstraints(Map<String, String> fields, Map<String, VariableConstraint> constraints) {
        for (var entry : constraints.entrySet()) {
            String value = fields.get(entry.getKey());
            VariableConstraint constraint = entry.getValue();
            if (value == null) {
                if (constraint.required()) return false;
                continue;
            }
            if (constraint instanceof VariableConstraint.EnumConstraint ec) {
                if (ec.values().stream().noneMatch(v -> v.equalsIgnoreCase(value))) return false;
            }
        }
        return true;
    }

    static CompiledTemplate compileTemplate(String template) {
        List<Segment> segments = parseSegments(template);
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        StringBuilder regex = new StringBuilder();
        for (Segment segment : segments) {
            if (segment.optional) {
                regex.append("(?:");
            }
            appendSegmentRegex(regex, segment.text, names, seen, segment.lastVarIsLast);
            if (segment.optional) {
                regex.append(")?");
            }
        }

        Pattern compiled = Pattern.compile(regex.toString());
        return new CompiledTemplate(compiled, Collections.unmodifiableList(names));
    }

    private static void appendSegmentRegex(StringBuilder regex, String text,
                                           List<String> names, Set<String> seen,
                                           boolean lastVarIsGlobal) {
        Matcher varMatcher = VAR_PATTERN.matcher(text);
        List<String> segNames = new ArrayList<>();
        List<String> segGroupNames = new ArrayList<>();
        List<int[]> positions = new ArrayList<>();

        while (varMatcher.find()) {
            String name = varMatcher.group(1);
            if (!VALID_VAR_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "Invalid variable name '" + name + "': must start with a letter, contain only letters, digits, dots and hyphens");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Duplicate variable name: " + name);
            }
            segNames.add(name);
            segGroupNames.add(sanitizeGroupName(name));
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
            regex.append("(?<").append(segGroupNames.get(i)).append(">").append(quantifier).append(")");
            lastEnd = pos[1];
        }

        if (lastEnd < text.length()) {
            regex.append(Pattern.quote(text.substring(lastEnd)));
        }

        names.addAll(segNames);
    }

    private static List<Segment> parseSegments(String template) {
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
                    throw new IllegalArgumentException("Nested optional segments are not supported");
                }
                if (current.length() > 0) {
                    segments.add(new Segment(current.toString(), false, false));
                    current.setLength(0);
                }
                inOptional = true;
                i++;
            } else if (c == ']') {
                if (!inOptional) {
                    throw new IllegalArgumentException("Unexpected ']' without matching '['");
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
            segments.set(segments.size() - 1, new Segment(last.text, last.optional, true));
        }

        return segments;
    }

    static String sanitizeGroupName(String name) {
        if (name.indexOf('.') < 0 && name.indexOf('-') < 0) return name;
        return name.replace(".", "0DOT0").replace("-", "0HYPHEN0");
    }

    record CompiledType(String name, List<CompiledTemplate> templates,
                        Map<String, VariableConstraint> variables) {}

    record CompiledTemplate(Pattern pattern, List<String> variableNames) {
        Optional<Map<String, String>> extract(String input) {
            Matcher m = pattern.matcher(input);
            if (!m.matches()) return Optional.empty();
            Map<String, String> result = new LinkedHashMap<>();
            for (String name : variableNames) {
                String groupName = sanitizeGroupName(name);
                String value = m.group(groupName);
                if (value != null) result.put(name, value);
            }
            return Optional.of(result);
        }
    }

    private record Segment(String text, boolean optional, boolean lastVarIsLast) {}
}
