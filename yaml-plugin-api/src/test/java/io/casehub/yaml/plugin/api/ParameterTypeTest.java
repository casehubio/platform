package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ParameterTypeTest {

    @Test
    void scalarTypesAreScalar() {
        assertTrue(ParameterType.STRING.isScalar());
        assertTrue(ParameterType.INTEGER.isScalar());
        assertTrue(ParameterType.NUMBER.isScalar());
        assertTrue(ParameterType.BOOLEAN.isScalar());
    }

    @Test
    void complexTypesAreNotScalar() {
        assertFalse(ParameterType.ARRAY.isScalar());
        assertFalse(ParameterType.OBJECT.isScalar());
    }

    @Test
    void validateAcceptsCorrectTypes() {
        assertTrue(ParameterType.STRING.validate("hello"));
        assertTrue(ParameterType.INTEGER.validate(42));
        assertTrue(ParameterType.INTEGER.validate(42L));
        assertTrue(ParameterType.NUMBER.validate(3.14));
        assertTrue(ParameterType.NUMBER.validate(42));
        assertTrue(ParameterType.BOOLEAN.validate(true));
        assertTrue(ParameterType.ARRAY.validate(java.util.List.of("a")));
        assertTrue(ParameterType.OBJECT.validate(java.util.Map.of("k", "v")));
    }

    @Test
    void validateRejectsWrongTypes() {
        assertFalse(ParameterType.STRING.validate(42));
        assertFalse(ParameterType.INTEGER.validate("hello"));
        assertFalse(ParameterType.BOOLEAN.validate("true"));
        assertFalse(ParameterType.ARRAY.validate("not a list"));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "yes,true", "on,true", "y,true", "1,true",
                "false,false", "no,false", "off,false", "n,false", "0,false"})
    void parseScalarBoolean(String input, boolean expected) {
        assertEquals(expected, ParameterType.BOOLEAN.parseScalar(input));
    }

    @Test
    void parseScalarString() { assertEquals("hello", ParameterType.STRING.parseScalar("hello")); }

    @Test
    void parseScalarInteger() { assertEquals(42, ParameterType.INTEGER.parseScalar("42")); }

    @Test
    void parseScalarNumber() { assertEquals(3.14, ParameterType.NUMBER.parseScalar("3.14")); }

    @Test
    void parseScalarRejectsComplexTypes() {
        assertThrows(IllegalArgumentException.class, () -> ParameterType.ARRAY.parseScalar("[]"));
        assertThrows(IllegalArgumentException.class, () -> ParameterType.OBJECT.parseScalar("{}"));
    }

    @Test
    void canAcceptSameType() {
        for (ParameterType t : ParameterType.values()) {
            assertTrue(t.canAccept(t), t + " should accept itself");
        }
    }

    @Test
    void stringAcceptsAllScalars() {
        assertTrue(ParameterType.STRING.canAccept(ParameterType.INTEGER));
        assertTrue(ParameterType.STRING.canAccept(ParameterType.NUMBER));
        assertTrue(ParameterType.STRING.canAccept(ParameterType.BOOLEAN));
    }

    @Test
    void stringDoesNotAcceptComplexTypes() {
        assertFalse(ParameterType.STRING.canAccept(ParameterType.ARRAY));
        assertFalse(ParameterType.STRING.canAccept(ParameterType.OBJECT));
    }

    @Test
    void numberAcceptsInteger() {
        assertTrue(ParameterType.NUMBER.canAccept(ParameterType.INTEGER));
    }

    @Test
    void fromStringHandlesAliases() {
        assertEquals(ParameterType.NUMBER, ParameterType.fromString("DECIMAL"));
        assertEquals(ParameterType.ARRAY, ParameterType.fromString("LIST"));
        assertEquals(ParameterType.ARRAY, ParameterType.fromString("list"));
    }

    @Test
    void fromStringRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ParameterType.fromString("UNKNOWN"));
    }
}
