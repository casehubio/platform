package io.casehub.yaml.core.step;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StepValidator {

    private StepValidator() {}

    public static List<String> validateStep(
            String actionName,
            Map<String, Object> params,
            StepDefinition definition) {
        return validateParams(actionName, params, definition.inputs(), "input");
    }

    public static List<String> validateOutputs(
            Map<String, Object> outputs,
            StepDefinition definition) {
        return validateParams(definition.name(), outputs, definition.outputs(), "output");
    }

    private static List<String> validateParams(
            String actionName,
            Map<String, Object> values,
            Map<String, StepParameter> declarations,
            String direction) {
        List<String> errors = new ArrayList<>();

        for (var entry : declarations.entrySet()) {
            String paramName = entry.getKey();
            StepParameter param = entry.getValue();
            Object value = values.get(paramName);

            if (value == null) {
                if (param.required()) {
                    errors.add(actionName + ": " + direction + " '" + paramName + "' is required but missing");
                }
                continue;
            }

            if (!param.type().validate(value)) {
                errors.add(actionName + ": " + direction + " '" + paramName
                        + "' expected type " + param.type() + " but got " + value.getClass().getSimpleName());
                continue;
            }

            if (!param.allowedValues().isEmpty() && value instanceof String s) {
                if (!param.allowedValues().contains(s)) {
                    errors.add(actionName + ": " + direction + " '" + paramName
                            + "' value '" + s + "' not in allowedValues " + param.allowedValues());
                }
            }

            if (param.format() != null && value instanceof String s) {
                String formatError = validateFormat(paramName, s, param.format());
                if (formatError != null) {
                    errors.add(actionName + ": " + direction + " " + formatError);
                }
            }
        }

        return errors;
    }

    private static String validateFormat(String paramName, String value, String format) {
        return switch (format) {
            case "date" -> {
                try {
                    LocalDate.parse(value);
                    yield null;
                } catch (Exception e) {
                    yield "'" + paramName + "' format 'date' validation failed: " + e.getMessage();
                }
            }
            case "date-time" -> {
                try {
                    OffsetDateTime.parse(value);
                    yield null;
                } catch (Exception e) {
                    yield "'" + paramName + "' format 'date-time' validation failed: " + e.getMessage();
                }
            }
            case "uri" -> {
                try {
                    URI.create(value);
                    yield null;
                } catch (Exception e) {
                    yield "'" + paramName + "' format 'uri' validation failed: " + e.getMessage();
                }
            }
            default -> null;
        };
    }
}
