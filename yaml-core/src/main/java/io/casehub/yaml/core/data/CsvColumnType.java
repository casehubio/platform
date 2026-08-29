package io.casehub.yaml.core.data;

import io.casehub.yaml.core.condition.Truthiness;

public enum CsvColumnType {
    STRING, INTEGER, BOOLEAN, DECIMAL;

    public Object parse(String value, int row, String columnName) {
        return switch (this) {
            case STRING -> value;
            case INTEGER -> parseInteger(value, row, columnName);
            case BOOLEAN -> parseBoolean(value, row, columnName);
            case DECIMAL -> parseDecimal(value, row, columnName);
        };
    }

    private static int parseInteger(String value, int row, String columnName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "CSV row " + row + ", column '" + columnName
                    + "': expected INTEGER, got '" + value + "'");
        }
    }

    private static boolean parseBoolean(String value, int row, String columnName) {
        try {
            return Truthiness.isTruthy(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "CSV row " + row + ", column '" + columnName
                    + "': expected BOOLEAN, got '" + value + "'");
        }
    }

    private static double parseDecimal(String value, int row, String columnName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "CSV row " + row + ", column '" + columnName
                    + "': expected DECIMAL, got '" + value + "'");
        }
    }
}
