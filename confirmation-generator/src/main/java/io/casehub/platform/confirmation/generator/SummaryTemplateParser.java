package io.casehub.platform.confirmation.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SummaryTemplateParser {

    private static final Pattern REF_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_.]*)}");

    private SummaryTemplateParser() {}

    public static List<String> extractReferences(String template) {
        List<String> refs = new ArrayList<>();
        Matcher m = REF_PATTERN.matcher(template);
        while (m.find()) {
            refs.add(m.group(1));
        }
        return refs;
    }

    public static String toJavaStringExpression(String template, List<String> refs) {
        StringBuilder sb = new StringBuilder();
        Matcher m = REF_PATTERN.matcher(template);
        int lastEnd = 0;
        while (m.find()) {
            String before = template.substring(lastEnd, m.start());
            if (!before.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" + ");
                sb.append("\"").append(escapeJava(before)).append("\"");
            }
            if (!sb.isEmpty()) sb.append(" + ");
            String ref = m.group(1);
            sb.append("String.valueOf(").append(toAccessor(ref)).append(")");
            lastEnd = m.end();
        }
        String tail = template.substring(lastEnd);
        if (!tail.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" + ");
            sb.append("\"").append(escapeJava(tail)).append("\"");
        }
        return sb.toString();
    }

    public static String toMetadataEntry(String ref) {
        String key = ref.contains(".") ? ref.substring(ref.lastIndexOf('.') + 1) : ref;
        return "\"" + key + "\", String.valueOf(" + toAccessor(ref) + ")";
    }

    static String toAccessor(String ref) {
        int dot = ref.indexOf('.');
        if (dot < 0) return ref;
        String root = ref.substring(0, dot);
        String field = ref.substring(dot + 1);
        return root + "." + field + "()";
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
