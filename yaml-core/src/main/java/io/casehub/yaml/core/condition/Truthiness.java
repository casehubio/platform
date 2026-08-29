package io.casehub.yaml.core.condition;

import java.util.Locale;

public final class Truthiness {

    private Truthiness() {}

    public static boolean isTruthy(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "y", "1" -> true;
            case "false", "no", "off", "n", "0" -> false;
            default -> throw new IllegalArgumentException(
                    "Condition resolved to '" + value
                    + "' which is not a boolean value. "
                    + "Expected: true/false/yes/no/on/off/y/n/1/0");
        };
    }
}
