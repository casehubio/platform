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
}
