package io.casehub.yaml.step.eval;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ThresholdCondition(String operator, double threshold, String metricName) {

    private static final Pattern PATTERN =
            Pattern.compile("^(>=|>|<=|<)?\\s*(\\d+(?:\\.\\d+)?)\\s+(\\w+)$");

    public static ThresholdCondition parse(String input) {
        Matcher m = PATTERN.matcher(input.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid threshold condition: '" + input + "'");
        }
        String op = m.group(1) != null ? m.group(1) : ">=";
        double threshold = Double.parseDouble(m.group(2));
        String metric = m.group(3);
        return new ThresholdCondition(op, threshold, metric);
    }

    public boolean test(double value) {
        return switch (operator) {
            case ">=" -> value >= threshold;
            case ">"  -> value > threshold;
            case "<=" -> value <= threshold;
            case "<"  -> value < threshold;
            default -> throw new IllegalStateException("Unknown operator: " + operator);
        };
    }
}
