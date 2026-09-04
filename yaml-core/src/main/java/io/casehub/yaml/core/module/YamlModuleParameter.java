package io.casehub.yaml.core.module;

import java.util.List;

public record YamlModuleParameter(
        ParameterType type,
        boolean required,
        String defaultValue,
        Integer minLength,
        Integer maxLength,
        String pattern,
        Number minimum,
        Number maximum,
        List<String> allowedValues,
        String constraintDescription) {

    public YamlModuleParameter {
        if (type == null) { type = ParameterType.STRING; }
        if (allowedValues == null) { allowedValues = List.of(); }
    }

    public static Builder builder() {return new Builder();}

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, YamlModuleParameter> fromDescriptors(
            List<java.util.Map<String, Object>> descriptors) {
        var result = new java.util.LinkedHashMap<String, YamlModuleParameter>();
        for (var desc : descriptors) {
            String        name         = (String) desc.get("name");
            String        typeName     = (String) desc.getOrDefault("type", "string");
            ParameterType type         = ParameterType.fromString(typeName);
            boolean       required     = Boolean.TRUE.equals(desc.get("required"));
            Object        rawDefault   = desc.get("default");
            String        defaultValue = rawDefault != null ? String.valueOf(rawDefault) : null;

            List<String> allowedValues = List.of();
            Object       enumVal       = desc.get("enum");
            if (enumVal instanceof List<?> enumList) {
                allowedValues = enumList.stream()
                                        .map(String::valueOf)
                                        .toList();
            }

            Number  minimum               = desc.get("min") instanceof Number n ? n : null;
            Number  maximum               = desc.get("max") instanceof Number n ? n : null;
            String  pattern               = desc.get("pattern") instanceof String s ? s : null;
            Integer minLength             = desc.get("minLength") instanceof Number n ? n.intValue() : null;
            Integer maxLength             = desc.get("maxLength") instanceof Number n ? n.intValue() : null;
            String  constraintDescription = desc.get("constraintDescription") instanceof String s ? s : null;

            result.put(name, new YamlModuleParameter(type, required, defaultValue,
                                                     minLength, maxLength, pattern, minimum, maximum, allowedValues,
                                                     constraintDescription));
        }
        return java.util.Collections.unmodifiableMap(result);
    }


    public static final class Builder {
        private ParameterType type          = ParameterType.STRING;
        private boolean       required;
        private String        defaultValue;
        private Integer       minLength;
        private Integer       maxLength;
        private String        pattern;
        private Number        minimum;
        private Number        maximum;
        private List<String>  allowedValues = List.of();
        private String        constraintDescription;

        private Builder()                                                  {}

        public Builder type(ParameterType type)                            {
                                                                               this.type = type;
                                                                               return this;
                                                                           }

        public Builder required(boolean required)                          {
                                                                               this.required = required;
                                                                               return this;
                                                                           }

        public Builder required()                                          {return required(true);}

        public Builder defaultValue(String defaultValue)                   {
                                                                               this.defaultValue = defaultValue;
                                                                               return this;
                                                                           }

        public Builder minLength(int minLength)                            {
                                                                               this.minLength = minLength;
                                                                               return this;
                                                                           }

        public Builder maxLength(int maxLength)                            {
                                                                               this.maxLength = maxLength;
                                                                               return this;
                                                                           }

        public Builder pattern(String pattern)                             {
                                                                               this.pattern = pattern;
                                                                               return this;
                                                                           }

        public Builder minimum(Number minimum)                             {
                                                                               this.minimum = minimum;
                                                                               return this;
                                                                           }

        public Builder maximum(Number maximum)                             {
                                                                               this.maximum = maximum;
                                                                               return this;
                                                                           }

        public Builder allowedValues(List<String> allowedValues)           {
                                                                               this.allowedValues = allowedValues;
                                                                               return this;
                                                                           }

        public Builder allowedValues(String... allowedValues)              {
                                                                               this.allowedValues = List.of(allowedValues);
                                                                               return this;
                                                                           }

        public Builder constraintDescription(String constraintDescription) {
                                                                               this.constraintDescription = constraintDescription;
                                                                               return this;
                                                                           }

        public YamlModuleParameter build() {
            validateCoherence();
            return new YamlModuleParameter(type, required, defaultValue, minLength, maxLength,
                                           pattern, minimum, maximum, allowedValues, constraintDescription);
        }

        private void validateCoherence() {
            if ((minimum != null || maximum != null)
                && type != ParameterType.INTEGER && type != ParameterType.NUMBER) {
                throw new IllegalArgumentException(
                        "minimum/maximum constraints are only valid for INTEGER and NUMBER parameters, not " + type
                        + ". Did you mean minLength/maxLength?");
            }
            if ((minLength != null || maxLength != null)
                && type != ParameterType.STRING && type != ParameterType.LIST) {
                throw new IllegalArgumentException(
                        "minLength/maxLength constraints are only valid for STRING and LIST parameters, not " + type
                        + ". Did you mean minimum/maximum?");
            }
            if (pattern != null && type != ParameterType.STRING && type != ParameterType.LIST) {
                throw new IllegalArgumentException(
                        "pattern constraint is only valid for STRING and LIST parameters, not " + type + ".");
            }
        }
    }
}
