package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticipantIdTest {

    @Test
    void of_wraps_actor() {
        ActorId a = ActorId.of(PrincipalId.human("alice"));
        ParticipantId p = ParticipantId.of(a);
        assertSame(a, p.actorId());
    }

    @Test
    void parse_delegates_through_chain() {
        ParticipantId p = ParticipantId.parse("agent:claude");
        assertEquals(ActorType.AGENT, p.type());
        assertEquals("claude", p.id());
        assertEquals("agent:claude", p.value());
    }

    @Test
    void principalId_shorthand() {
        PrincipalId principal = PrincipalId.human("alice");
        ParticipantId p = ParticipantId.of(ActorId.of(principal));
        assertSame(principal, p.principalId());
    }

    @Test
    void type_delegates() {
        ParticipantId p = ParticipantId.of(ActorId.of(PrincipalId.system("bot")));
        assertEquals(ActorType.SYSTEM, p.type());
    }

    @Test
    void value_delegates() {
        ParticipantId p = ParticipantId.of(ActorId.of(PrincipalId.agent("claude:analyst@v1")));
        assertEquals("agent:claude:analyst@v1", p.value());
    }

    @Test
    void toString_returns_value() {
        ParticipantId p = ParticipantId.of(ActorId.of(PrincipalId.human("alice")));
        assertEquals("human:alice", p.toString());
    }

    @Test
    void constructor_rejects_null() {
        assertThrows(NullPointerException.class, () -> new ParticipantId(null));
    }

    @Test
    void implements_identity() {
        assertInstanceOf(Identity.class,
            ParticipantId.of(ActorId.of(PrincipalId.human("x"))));
    }

    @Test
    void equals_based_on_actor() {
        ParticipantId a = ParticipantId.of(ActorId.of(PrincipalId.human("alice")));
        ParticipantId b = ParticipantId.of(ActorId.of(PrincipalId.human("alice")));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
