package io.casehub.platform.api.view;

import java.util.Objects;

public final class LabelPatternMatcher {

    private LabelPatternMatcher() {}

    public static boolean matches(String pattern, String path) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(path, "path must not be null");

        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix + "/");
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            if (!path.startsWith(prefix + "/")) {
                return false;
            }
            String remainder = path.substring(prefix.length() + 1);
            return !remainder.contains("/");
        }
        return pattern.equals(path);
    }
}
