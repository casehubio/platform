package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResourceIdTest {

    @Test
    void constructsWithTypeAndId() {
        var rid = new ResourceId("case", "abc-123");
        assertEquals("case", rid.type());
        assertEquals("abc-123", rid.id());
    }

    @Test
    void toStringFormatsAsTypeColonId() {
        assertEquals("case:abc-123", new ResourceId("case", "abc-123").toString());
    }

    @Test
    void parseRoundTrips() {
        var rid = ResourceId.parse("case:abc-123");
        assertEquals("case", rid.type());
        assertEquals("abc-123", rid.id());
    }

    @Test
    void parseHandlesColonInId() {
        var rid = ResourceId.parse("ns:sub:value");
        assertEquals("ns", rid.type());
        assertEquals("sub:value", rid.id());
    }

    @Test
    void rejectsBlankType() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceId("", "id"));
    }

    @Test
    void rejectsNullType() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceId(null, "id"));
    }

    @Test
    void rejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceId("case", ""));
    }

    @Test
    void rejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceId("case", null));
    }

    @Test
    void rejectsColonInType() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceId("a:b", "id"));
    }

    @Test
    void parseRejectsNoColon() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("nocolon"));
    }

    @Test
    void parseRejectsLeadingColon() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse(":id"));
    }

    @Test
    void parseRejectsTrailingColon() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("type:"));
    }

    @Test
    void equalityByValue() {
        assertEquals(new ResourceId("case", "123"), new ResourceId("case", "123"));
        assertNotEquals(new ResourceId("case", "123"), new ResourceId("case", "456"));
        assertNotEquals(new ResourceId("case", "123"), new ResourceId("plan", "123"));
    }
}
