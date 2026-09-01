package io.casehub.yaml.core.module;

import java.util.List;

public sealed interface ParsedValue permits ParsedValue.StringValue, ParsedValue.IntegerValue,
        ParsedValue.NumberValue, ParsedValue.BooleanValue, ParsedValue.ListValue {

    Object raw();

    record StringValue(String value) implements ParsedValue {
        @Override public Object raw() { return value; }
    }

    record IntegerValue(int value) implements ParsedValue {
        @Override public Object raw() { return value; }
    }

    record NumberValue(double value) implements ParsedValue {
        @Override public Object raw() { return value; }
    }

    record BooleanValue(boolean value) implements ParsedValue {
        @Override public Object raw() { return value; }
    }

    record ListValue(List<String> value) implements ParsedValue {
        @Override public Object raw() { return value; }
    }
}
