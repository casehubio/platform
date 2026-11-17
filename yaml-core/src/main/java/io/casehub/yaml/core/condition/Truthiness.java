package io.casehub.yaml.core.condition;

public final class Truthiness {

    private Truthiness() {}

    public static boolean isTruthy(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(
                    "Condition resolved to '" + value
                    + "' which is not a boolean literal. "
                    + "Expected: true/false. Use an expression engine for complex conditions.");
        };
    }

    public static boolean isYamlTruthy(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on", "y", "1" -> true;
            case "false", "no", "off", "n", "0" -> false;
            default -> throw new IllegalArgumentException(
                    "'" + value + "' is not a YAML boolean value. "
                    + "Expected: true/false/yes/no/on/off/y/n/1/0");
        };
    }

}
