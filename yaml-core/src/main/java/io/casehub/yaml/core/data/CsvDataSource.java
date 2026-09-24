package io.casehub.yaml.core.data;

import io.casehub.yaml.core.type.TypedSchema;
import io.casehub.yaml.core.type.ValueType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record CsvDataSource(String name, List<CsvColumn> columns,
                             List<Map<String, Object>> rows) implements TypedSchema {

    @Override
    public ValueType typeOf(String columnName) {
        return columns.stream()
                .filter(c -> c.name().equals(columnName))
                .map(CsvColumn::type)
                .findFirst().orElse(null);
    }

    @Override
    public Map<String, ValueType> schema() {
        return columns.stream()
                .collect(Collectors.toMap(CsvColumn::name, CsvColumn::type,
                                          (a, b) -> a, LinkedHashMap::new));
    }
    public static java.util.Map<String, CsvDataSource> fromDataBlock(java.util.Map<String, Object> data) {
        var sources = new java.util.LinkedHashMap<String, CsvDataSource>();
        for (var entry : data.entrySet()) {
            if (entry.getValue() instanceof java.util.Map<?, ?> spec) {
                Object inlineVal = spec.get("inline");
                if (inlineVal != null) {
                    sources.put(entry.getKey(), CsvParser.parse(entry.getKey(), String.valueOf(inlineVal)));
                }
            }
        }
        return java.util.Collections.unmodifiableMap(sources);
    }
}
