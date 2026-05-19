package io.casehub.platform.converter;

import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathParamConverterTest {

    private final PathParamConverter converter = new PathParamConverter();

    @Test
    void fromString_parses_slash_separated_value() {
        Path p = converter.fromString("casehubio/devtown/pr-review");
        assertEquals("casehubio/devtown/pr-review", p.value());
        assertEquals(3, p.depth());
    }

    @Test
    void fromString_returns_null_for_null_input() {
        assertNull(converter.fromString(null));
    }

    @Test
    void fromString_throws_for_invalid_input() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.fromString("casehubio//devtown"));
    }

    @Test
    void toString_returns_path_value() {
        Path p = Path.of("casehubio", "devtown", "pr-review");
        assertEquals("casehubio/devtown/pr-review", converter.toString(p));
    }

    @Test
    void toString_returns_null_for_null_input() {
        assertNull(converter.toString(null));
    }

    @Test
    void roundtrip_fromString_toString() {
        String original = "casehubio/devtown/pr-review";
        Path p = converter.fromString(original);
        assertEquals(original, converter.toString(p));
    }
}
