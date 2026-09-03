package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorIdTest {

    @Test
    void of_wraps_principal() {
        PrincipalId p = PrincipalId.human("alice");
        ActorId a = ActorId.of(p);
        assertSame(p, a.principalId());
    }

    @Test
    void parse_delegates_to_principalId() {
        ActorId a = ActorId.parse("agent:claude");
        assertEquals(ActorType.AGENT, a.type());
        assertEquals("claude", a.id());
        assertEquals("agent:claude", a.value());
    }

    @Test
    void type_delegates() {
        ActorId a = ActorId.of(PrincipalId.system("cron"));
        assertEquals(ActorType.SYSTEM, a.type());
    }

    @Test
    void id_delegates() {
        ActorId a = ActorId.of(PrincipalId.human("bob"));
        assertEquals("bob", a.id());
    }

    @Test
    void value_delegates() {
        ActorId a = ActorId.of(PrincipalId.agent("claude:analyst@v1"));
        assertEquals("agent:claude:analyst@v1", a.value());
    }

    @Test
    void toString_returns_value() {
        ActorId a = ActorId.of(PrincipalId.human("alice"));
        assertEquals("human:alice", a.toString());
    }

    @Test
    void constructor_rejects_null() {
        assertThrows(NullPointerException.class, () -> new ActorId(null));
    }

    @Test
    void implements_identity() {
        assertInstanceOf(Identity.class, ActorId.of(PrincipalId.human("x")));
    }

    @Test
    void equals_based_on_principal() {
        ActorId a = ActorId.of(PrincipalId.human("alice"));
        ActorId b = ActorId.of(PrincipalId.human("alice"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void not_equal_to_different_principal() {
        assertNotEquals(
            ActorId.of(PrincipalId.human("alice")),
            ActorId.of(PrincipalId.human("bob")));
    }
}
