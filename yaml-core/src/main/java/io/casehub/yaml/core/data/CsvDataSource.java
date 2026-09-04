package io.casehub.yaml.core.data;

import java.util.List;
import java.util.Map;

public record CsvDataSource(String name, List<CsvColumn> columns,
                             List<Map<String, Object>> rows) {
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
