package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ActorTypeTest {

    @Test
    void human_prefix_is_human() {
        assertEquals("human", ActorType.HUMAN.prefix());
    }

    @Test
    void agent_prefix_is_agent() {
        assertEquals("agent", ActorType.AGENT.prefix());
    }

    @Test
    void system_prefix_is_system() {
        assertEquals("system", ActorType.SYSTEM.prefix());
    }

    @Test
    void fromPrefix_resolves_lowercase() {
        assertEquals(ActorType.HUMAN, ActorType.fromPrefix("human"));
        assertEquals(ActorType.AGENT, ActorType.fromPrefix("agent"));
        assertEquals(ActorType.SYSTEM, ActorType.fromPrefix("system"));
    }

    @Test
    void fromPrefix_is_case_insensitive() {
        assertEquals(ActorType.HUMAN, ActorType.fromPrefix("HUMAN"));
        assertEquals(ActorType.AGENT, ActorType.fromPrefix("Agent"));
    }

    @Test
    void fromPrefix_rejects_unknown() {
        assertThrows(IllegalArgumentException.class, () -> ActorType.fromPrefix("robot"));
    }

    @ParameterizedTest
    @EnumSource(ActorType.class)
    void prefix_roundtrips_through_fromPrefix(ActorType type) {
        assertEquals(type, ActorType.fromPrefix(type.prefix()));
    }
}
