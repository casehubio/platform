package io.casehub.platform.mcp;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InlineSchemaBuilder {

    private InlineSchemaBuilder() {}

    public static Map<String, Object> buildToolSchema(List<ParameterDescriptor> params,
                                                       java.lang.reflect.Parameter[] methodParams) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (int i = 0; i < params.size(); i++) {
            ParameterDescriptor pd = params.get(i);
            Map<String, Object> propSchema = typeToSchema(methodParams[i].getParameterizedType());
            if (pd.description() != null && !pd.description().isBlank()) {
                propSchema.put("description", pd.description());
            }
            properties.put(pd.name(), propSchema);
            if (pd.required()) {
                required.add(pd.name());
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    static Map<String, Object> typeToSchema(Type type) {
        if (type instanceof ParameterizedType pt) {
            return parameterizedTypeToSchema(pt);
        }
        if (type instanceof Class<?> clazz) {
            return classToSchema(clazz);
        }
        return Map.of("type", "string");
    }

    private static Map<String, Object> parameterizedTypeToSchema(ParameterizedType pt) {
        Class<?> raw = (Class<?>) pt.getRawType();

        if (List.class.isAssignableFrom(raw) || Set.class.isAssignableFrom(raw)
            || Collection.class.isAssignableFrom(raw)) {
            Type itemType = pt.getActualTypeArguments()[0];
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", typeToSchema(itemType));
            return schema;
        }

        if (Map.class.isAssignableFrom(raw)) {
            Type valueType = pt.getActualTypeArguments().length > 1
                             ? pt.getActualTypeArguments()[1] : String.class;
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("additionalProperties", typeToSchema(valueType));
            return schema;
        }

        return classToSchema(raw);
    }

    private static Map<String, Object> classToSchema(Class<?> clazz) {
        if (clazz == String.class || clazz == CharSequence.class) {
            return new LinkedHashMap<>(Map.of("type", "string"));
        }
        if (clazz == int.class || clazz == Integer.class
            || clazz == long.class || clazz == Long.class
            || clazz == short.class || clazz == Short.class
            || clazz == byte.class || clazz == Byte.class) {
            return new LinkedHashMap<>(Map.of("type", "integer"));
        }
        if (clazz == double.class || clazz == Double.class
            || clazz == float.class || clazz == Float.class
            || java.math.BigDecimal.class.isAssignableFrom(clazz)
            || java.math.BigInteger.class.isAssignableFrom(clazz)) {
            return new LinkedHashMap<>(Map.of("type", "number"));
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return new LinkedHashMap<>(Map.of("type", "boolean"));
        }
        if (clazz == UUID.class) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "string");
            schema.put("format", "uuid");
            return schema;
        }
        if (clazz == Instant.class || clazz == OffsetDateTime.class || clazz == ZonedDateTime.class) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "string");
            schema.put("format", "date-time");
            return schema;
        }
        if (clazz == LocalDate.class) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "string");
            schema.put("format", "date");
            return schema;
        }
        if (clazz == LocalTime.class) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "string");
            schema.put("format", "time");
            return schema;
        }
        if (clazz == LocalDateTime.class) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "string");
            schema.put("format", "date-time");
            return schema;
        }
        if (clazz.isEnum()) {
            List<String> values = new ArrayList<>();
            for (Object c : clazz.getEnumConstants()) {
                values.add(((Enum<?>) c).name());
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "string");
            schema.put("enum", values);
            return schema;
        }
        if (clazz.isSealed()) {
            return sealedToSchema(clazz);
        }
        if (clazz.isRecord()) {
            return recordToSchema(clazz);
        }
        if (clazz == Object.class) {
            return new LinkedHashMap<>(Map.of("type", "object"));
        }
        return pojoToSchema(clazz);
    }

    private static Map<String, Object> recordToSchema(Class<?> recordClass) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (RecordComponent rc : recordClass.getRecordComponents()) {
            properties.put(rc.getName(), typeToSchema(rc.getGenericType()));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> sealedToSchema(Class<?> sealedClass) {
        List<Map<String, Object>> variants = new ArrayList<>();
        for (Class<?> permit : sealedClass.getPermittedSubclasses()) {
            variants.add(typeToSchema(permit));
        }
        if (variants.size() == 1) {
            return variants.get(0);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("oneOf", variants);
        return schema;
    }


    private static Map<String, Object> pojoToSchema(Class<?> clazz) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() == void.class) continue;
            String name = m.getName();
            if (name.startsWith("get") && name.length() > 3) {
                name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2
                       && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                name = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            } else {
                continue;
            }
            properties.put(name, typeToSchema(m.getGenericReturnType()));
        }
        if (properties.isEmpty()) {
            return new LinkedHashMap<>(Map.of("type", "object"));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }
}
