package io.casehub.yaml.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CsvParser {

    private CsvParser() {}

    public static CsvDataSource parse(String name, String csvContent) {
        List<String> lines = csvContent.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();

        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV content is empty — expected at least a header row.");
        }

        List<CsvColumn> columns = parseHeader(lines.get(0));
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            rows.add(parseRow(lines.get(i), columns, i));
        }

        return new CsvDataSource(name, List.copyOf(columns), List.copyOf(rows));
    }

    private static List<CsvColumn> parseHeader(String headerLine) {
        String[] parts = headerLine.split(",");
        List<CsvColumn> columns = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException(
                        "Header column '" + trimmed
                        + "' must use format columnName:TYPE (e.g. name:STRING).");
            }
            String colName = trimmed.substring(0, colon).trim();
            String typeName = trimmed.substring(colon + 1).trim().toUpperCase(Locale.ROOT);
            CsvColumnType type;
            try {
                type = CsvColumnType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown column type '" + typeName
                        + "' for column '" + colName
                        + "'. Expected: STRING, INTEGER, BOOLEAN, DECIMAL.");
            }
            columns.add(new CsvColumn(colName, type));
        }
        return columns;
    }

    private static Map<String, Object> parseRow(String line, List<CsvColumn> columns, int rowIndex) {
        String[] values = line.split(",", -1);
        if (values.length != columns.size()) {
            throw new IllegalArgumentException(
                    "CSV row " + rowIndex + " has " + values.length
                    + " values but header declares " + columns.size() + " columns.");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            CsvColumn col = columns.get(i);
            String value = values[i].trim();
            row.put(col.name(), col.type().parse(value, rowIndex, col.name()));
        }
        return Collections.unmodifiableMap(row);
    }
}
