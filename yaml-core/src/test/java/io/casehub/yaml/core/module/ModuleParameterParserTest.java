package io.casehub.yaml.core.module;

import io.casehub.yaml.plugin.api.ParameterType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModuleParameterParserTest {

    @Test
    void parseString() {
        ParsedValue result = ModuleParameterParser.parse(ParameterType.STRING, "hello");
        assertInstanceOf(ParsedValue.StringValue.class, result);
        assertEquals("hello", ((ParsedValue.StringValue) result).value());
    }

    @Test
    void parseArray() {
        ParsedValue result = ModuleParameterParser.parse(ParameterType.ARRAY, "a, b, c");
        assertInstanceOf(ParsedValue.ListValue.class, result);
        assertEquals(List.of("a", "b", "c"), ((ParsedValue.ListValue) result).value());
    }

    @Test
    void parseInteger() {
        ParsedValue result = ModuleParameterParser.parse(ParameterType.INTEGER, "42");
        assertInstanceOf(ParsedValue.IntegerValue.class, result);
    }

    @Test
    void parseObjectThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ModuleParameterParser.parse(ParameterType.OBJECT, "{}"));
    }
}
