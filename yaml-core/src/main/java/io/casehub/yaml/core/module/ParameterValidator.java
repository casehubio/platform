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
                violations.add(createViolation(entry.getKey(), "unknown",
                        "Parameter '" + entry.getKey() + "' is not declared. "
                        + "Available: " + declared.keySet(), entry.getValue(), null));
            }
        }

        for (Map.Entry<String, YamlModuleParameter> entry : declared.entrySet()) {
            String name = entry.getKey();
            YamlModuleParameter param = entry.getValue();
            String value = provided.get(name);

            if (value == null) {
                if (param.required() && param.defaultValue() == null) {
                    violations.add(createViolation(name, "required",
                            "Required parameter '" + name + "' is missing.", null,
                            param.constraintDescription()));
                }
                continue;
            }

            ParsedValue parsed;
            try {
                parsed = param.type().parse(value);
            } catch (Exception e) {
                violations.add(createViolation(name, "type",
                        "Parameter '" + name + "': expected " + param.type()
                        + ", got '" + value + "'.", value,
                        param.constraintDescription()));
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
            String rawValue, ParsedValue parsed, List<ParameterViolation> violations) {
        if (param.minLength() != null) {
            int length = lengthOf(param, rawValue, parsed);
            if (length < param.minLength()) {
                violations.add(createViolation(name, "minLength",
                                               "Parameter '" + name + "': length " + length
                                               + " is less than minimum " + param.minLength() + ".", rawValue,
                                               param.constraintDescription()));
            }
        }

        if (param.maxLength() != null) {
            int length = lengthOf(param, rawValue, parsed);
            if (length > param.maxLength()) {
                violations.add(createViolation(name, "maxLength",
                                               "Parameter '" + name + "': length " + length
                                               + " exceeds maximum " + param.maxLength() + ".", rawValue,
                                               param.constraintDescription()));
            }
        }

        if (param.pattern() != null) {
            if (parsed instanceof ParsedValue.ListValue lv) {
                for (String item : lv.value()) {
                    if (!Pattern.matches(param.pattern(), item)) {
                        violations.add(createViolation(name, "pattern",
                                                       "Parameter '" + name + "': element '" + item
                                                       + "' does not match pattern '" + param.pattern() + "'.",
                                                       rawValue, param.constraintDescription()));
                    }
                }
            } else {
                if (!Pattern.matches(param.pattern(), rawValue)) {
                    violations.add(createViolation(name, "pattern",
                                                   "Parameter '" + name + "': value '" + rawValue
                                                   + "' does not match pattern '" + param.pattern() + "'.",
                                                   rawValue, param.constraintDescription()));
                }
            }
        }

        if (param.minimum() != null) {
            double numValue = numericValue(parsed);
            if (!Double.isNaN(numValue) && numValue < param.minimum().doubleValue()) {
                violations.add(createViolation(name, "minimum",
                                               "Parameter '" + name + "': value " + rawValue
                                               + " is less than minimum " + param.minimum() + ".",
                                               rawValue, param.constraintDescription()));
            }
        }

        if (param.maximum() != null) {
            double numValue = numericValue(parsed);
            if (!Double.isNaN(numValue) && numValue > param.maximum().doubleValue()) {
                violations.add(createViolation(name, "maximum",
                                               "Parameter '" + name + "': value " + rawValue
                                               + " exceeds maximum " + param.maximum() + ".",
                                               rawValue, param.constraintDescription()));
            }
        }

        if (!param.allowedValues().isEmpty()) {
            boolean matches = param.allowedValues().contains(rawValue);
            if (!matches) {
                String canonical = switch (parsed) {
                    case ParsedValue.IntegerValue iv -> String.valueOf(iv.value());
                    case ParsedValue.NumberValue nv -> String.valueOf(nv.value());
                    case ParsedValue.BooleanValue bv -> String.valueOf(bv.value());
                    default -> null;
                };
                if (canonical != null) {
                    matches = param.allowedValues().contains(canonical);
                }
            }
            if (!matches) {
                String technical = "Parameter '" + name + "': value '" + rawValue
                                   + "' is not one of " + param.allowedValues() + ".";
                violations.add(createViolation(name, "allowedValues", technical,
                                               rawValue, param.constraintDescription()));
            }
        }
    }


    private static double numericValue(ParsedValue parsed) {
        if (parsed instanceof ParsedValue.IntegerValue iv) {return iv.value();}
        if (parsed instanceof ParsedValue.NumberValue nv) {return nv.value();}
        return Double.NaN;
    }

    private static int lengthOf(YamlModuleParameter param, String rawValue, ParsedValue parsed) {
        if (parsed instanceof ParsedValue.ListValue lv) {return lv.value().size();}
        return rawValue.length();
    }

    private static ParameterViolation createViolation(String name, String constraint,
                                                      String technicalMessage, Object actualValue, String constraintDescription) {
        if (constraintDescription != null) {
            return new ParameterViolation(name, constraint, constraintDescription,
                                          actualValue, technicalMessage);
        }
        return new ParameterViolation(name, constraint, technicalMessage,
                                      actualValue, technicalMessage);
    }

}
