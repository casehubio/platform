package io.casehub.yaml.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ParameterValidator {

    private ParameterValidator() {}

    public static List<ParameterViolation> validate(
            Map<String, YamlModuleParameter> declared,
            Map<String, String> provided) {

        List<ParameterViolation> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : provided.entrySet()) {
            if (!declared.containsKey(entry.getKey())) {
                violations.add(new ParameterViolation(entry.getKey(), "unknown",
                        "Parameter '" + entry.getKey() + "' is not declared. "
                        + "Available: " + declared.keySet(), entry.getValue()));
            }
        }

        for (Map.Entry<String, YamlModuleParameter> entry : declared.entrySet()) {
            String name = entry.getKey();
            YamlModuleParameter param = entry.getValue();
            String value = provided.get(name);

            if (value == null) {
                if (param.required() && param.defaultValue() == null) {
                    violations.add(new ParameterViolation(name, "required",
                            "Required parameter '" + name + "' is missing.", null));
                }
                continue;
            }

            Object parsed;
            try {
                parsed = param.type().parse(value);
            } catch (Exception e) {
                violations.add(new ParameterViolation(name, "type",
                        "Parameter '" + name + "': expected " + param.type()
                        + ", got '" + value + "'.", value));
                continue;
            }

            validateConstraints(name, param, value, parsed, violations);
        }

        return List.copyOf(violations);
    }

    public static void validateOrThrow(
            Map<String, YamlModuleParameter> declared,
            Map<String, String> provided) {
        List<ParameterViolation> violations = validate(declared, provided);
        if (!violations.isEmpty()) {
            throw new ParameterValidationException(violations);
        }
    }

    private static void validateConstraints(String name, YamlModuleParameter param,
            String rawValue, Object parsed, List<ParameterViolation> violations) {

        if (param.minLength() != null) {
            int length = lengthOf(param, rawValue, parsed);
            if (length < param.minLength()) {
                violations.add(new ParameterViolation(name, "minLength",
                        "Parameter '" + name + "': length " + length
                        + " is less than minimum " + param.minLength() + ".", rawValue));
            }
        }

        if (param.maxLength() != null) {
            int length = lengthOf(param, rawValue, parsed);
            if (length > param.maxLength()) {
                violations.add(new ParameterViolation(name, "maxLength",
                        "Parameter '" + name + "': length " + length
                        + " exceeds maximum " + param.maxLength() + ".", rawValue));
            }
        }

        if (param.pattern() != null) {
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (!Pattern.matches(param.pattern(), item.toString())) {
                        violations.add(new ParameterViolation(name, "pattern",
                                "Parameter '" + name + "': element '" + item
                                + "' does not match pattern '" + param.pattern() + "'.",
                                rawValue));
                    }
                }
            } else {
                if (!Pattern.matches(param.pattern(), rawValue)) {
                    violations.add(new ParameterViolation(name, "pattern",
                            "Parameter '" + name + "': value '" + rawValue
                            + "' does not match pattern '" + param.pattern() + "'.",
                            rawValue));
                }
            }
        }

        if (param.minimum() != null && parsed instanceof Number num) {
            if (num.doubleValue() < param.minimum().doubleValue()) {
                violations.add(new ParameterViolation(name, "minimum",
                        "Parameter '" + name + "': value " + num
                        + " is less than minimum " + param.minimum() + ".",
                        rawValue));
            }
        }

        if (param.maximum() != null && parsed instanceof Number num) {
            if (num.doubleValue() > param.maximum().doubleValue()) {
                violations.add(new ParameterViolation(name, "maximum",
                        "Parameter '" + name + "': value " + num
                        + " exceeds maximum " + param.maximum() + ".",
                        rawValue));
            }
        }
    }

    private static int lengthOf(YamlModuleParameter param, String rawValue, Object parsed) {
        if (parsed instanceof List<?> list) { return list.size(); }
        return rawValue.length();
    }
}
