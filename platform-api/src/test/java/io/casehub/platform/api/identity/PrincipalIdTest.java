package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrincipalIdTest {

    @Test
    void parse_human_id() {
        PrincipalId p = PrincipalId.parse("human:john.smith");
        assertEquals(ActorType.HUMAN, p.type());
        assertEquals("john.smith", p.id());
        assertEquals("human:john.smith", p.value());
    }

    @Test
    void parse_agent_id_with_nested_colons() {
        PrincipalId p = PrincipalId.parse("agent:claude:analyst@v1");
        assertEquals(ActorType.AGENT, p.type());
        assertEquals("claude:analyst@v1", p.id());
        assertEquals("agent:claude:analyst@v1", p.value());
    }

    @Test
    void parse_system_id() {
        PrincipalId p = PrincipalId.parse("system:scheduler");
        assertEquals(ActorType.SYSTEM, p.type());
        assertEquals("scheduler", p.id());
    }

    @Test
    void parse_is_case_insensitive_on_type() {
        PrincipalId p = PrincipalId.parse("AGENT:worker");
        assertEquals(ActorType.AGENT, p.type());
        assertEquals("worker", p.id());
    }

    @Test
    void parse_rejects_missing_colon() {
        assertThrows(IllegalArgumentException.class, () -> PrincipalId.parse("nocolon"));
    }

    @Test
    void parse_rejects_unknown_type() {
        assertThrows(IllegalArgumentException.class, () -> PrincipalId.parse("robot:x"));
    }

    @Test
    void parse_rejects_blank_id() {
        assertThrows(IllegalArgumentException.class, () -> PrincipalId.parse("human:"));
    }

    @Test
    void parse_rejects_whitespace_id() {
        assertThrows(IllegalArgumentException.class, () -> PrincipalId.parse("human:   "));
    }

    @Test
    void constructor_rejects_null_type() {
        assertThrows(NullPointerException.class, () -> new PrincipalId(null, "id"));
    }

    @Test
    void constructor_rejects_null_id() {
        assertThrows(IllegalArgumentException.class, () -> new PrincipalId(ActorType.HUMAN, null));
    }

    @Test
    void factory_human() {
        PrincipalId p = PrincipalId.human("alice");
        assertEquals(ActorType.HUMAN, p.type());
        assertEquals("alice", p.id());
    }

    @Test
    void factory_agent() {
        PrincipalId p = PrincipalId.agent("claude");
        assertEquals(ActorType.AGENT, p.type());
        assertEquals("claude", p.id());
    }

    @Test
    void factory_system() {
        PrincipalId p = PrincipalId.system("scheduler");
        assertEquals(ActorType.SYSTEM, p.type());
        assertEquals("scheduler", p.id());
    }

    @Test
    void value_roundtrips_through_parse() {
        PrincipalId original = PrincipalId.agent("claude:analyst@v1");
        PrincipalId parsed = PrincipalId.parse(original.value());
        assertEquals(original, parsed);
    }

    @Test
    void toString_returns_value() {
        PrincipalId p = PrincipalId.human("alice");
        assertEquals("human:alice", p.toString());
    }

    @Test
    void equals_and_hashCode_from_record() {
        PrincipalId a = PrincipalId.human("alice");
        PrincipalId b = PrincipalId.human("alice");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void not_equal_different_type() {
        assertNotEquals(PrincipalId.human("x"), PrincipalId.agent("x"));
    }

    @Test
    void not_equal_different_id() {
        assertNotEquals(PrincipalId.human("alice"), PrincipalId.human("bob"));
    }

    @Test
    void implements_identity() {
        PrincipalId p = PrincipalId.human("alice");
        assertInstanceOf(Identity.class, p);
    }
}
