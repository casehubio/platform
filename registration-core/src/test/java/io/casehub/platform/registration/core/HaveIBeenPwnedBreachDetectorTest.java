package io.casehub.platform.registration.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HaveIBeenPwnedBreachDetectorTest {

    @Test
    void sha1Hex_produces40CharHex() {
        String hash = HaveIBeenPwnedBreachDetector.sha1Hex("password");
        assertEquals(40, hash.length());
    }

    @Test
    void parsesBreachedSuffix() {
        String hash = HaveIBeenPwnedBreachDetector.sha1Hex("password");
        String suffix = hash.substring(5).toUpperCase();
        String responseBody = "0000000000000000000000000000000000A:1\r\n"
                + suffix + ":12345\r\n"
                + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2\r\n";
        assertTrue(HaveIBeenPwnedBreachDetector.containsSuffix(responseBody, suffix));
    }

    @Test
    void doesNotMatchAbsentSuffix() {
        String responseBody = "0000000000000000000000000000000000A:1\r\n"
                + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2\r\n";
        assertFalse(HaveIBeenPwnedBreachDetector.containsSuffix(responseBody, "ABCDE12345678901234567890123456789AB"));
    }
}
