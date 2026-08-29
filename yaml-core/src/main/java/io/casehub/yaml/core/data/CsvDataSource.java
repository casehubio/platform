package io.casehub.yaml.core.data;

import java.util.List;
import java.util.Map;

public record CsvDataSource(String name, List<CsvColumn> columns,
                             List<Map<String, Object>> rows) {}
