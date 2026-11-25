package io.casehub.template.registry;

import java.util.regex.Pattern;

public final class TemplateFormatHelper {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private TemplateFormatHelper() {}

    public static String formatSuggestion(String pattern) {
        String stripped = pattern.replace("[", "").replace("]", "");
        return VAR_PATTERN.matcher(stripped).replaceAll(mr -> {
            String varName = mr.group(1);
            return "[" + varName + "]";
        });
    }
}
