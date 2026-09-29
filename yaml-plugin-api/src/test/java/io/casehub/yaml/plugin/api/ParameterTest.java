package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParameterTest {

    @Test
    void defaultsToString() {
        var p = new Parameter(null, false, null, null, null, null);
        assertEquals(ParameterType.STRING, p.type());
    }

    @Test
    void defaultsToEmptyAllowedValues() {
        var p = new Parameter(ParameterType.STRING, false, null, null, null, null);
        assertTrue(p.allowedValues().isEmpty());
    }

    @Test
    void rejectsDefaultValueOnComplexType() {
        assertThrows(IllegalArgumentException.class, () ->
            new Parameter(ParameterType.ARRAY, false, "[]", null, null, null));
    }

    @Test
    void acceptsDefaultValueOnScalarType() {
        var p = new Parameter(ParameterType.INTEGER, false, "42", null, null, "count");
        assertEquals("42", p.defaultValue());
    }
}
