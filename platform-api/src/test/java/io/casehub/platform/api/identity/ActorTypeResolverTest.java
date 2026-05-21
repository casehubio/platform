package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorTypeResolverTest {

    // Rule 1: null or blank → SYSTEM
    @Test
    void null_actorId_resolves_to_system() {
        assertEquals(ActorType.SYSTEM, ActorTypeResolver.resolve(null));
    }

    @Test
    void blank_actorId_resolves_to_system() {
        assertEquals(ActorType.SYSTEM, ActorTypeResolver.resolve("   "));
    }

    @Test
    void empty_actorId_resolves_to_system() {
        assertEquals(ActorType.SYSTEM, ActorTypeResolver.resolve(""));
    }

    // Rule 2: "system" or "system:*" → SYSTEM
    @Test
    void system_literal_resolves_to_system() {
        assertEquals(ActorType.SYSTEM, ActorTypeResolver.resolve("system"));
    }

    @Test
    void system_colon_prefix_resolves_to_system() {
        assertEquals(ActorType.SYSTEM, ActorTypeResolver.resolve("system:scheduler"));
    }

    // Rule 3: "agent:*" → AGENT
    @Test
    void agent_colon_prefix_resolves_to_agent() {
        assertEquals(ActorType.AGENT, ActorTypeResolver.resolve("agent:worker-1"));
    }

    // Rule 4: versioned persona format word:word@version → AGENT
    @Test
    void versioned_persona_resolves_to_agent() {
        assertEquals(ActorType.AGENT, ActorTypeResolver.resolve("claude:analyst@v1"));
    }

    @Test
    void versioned_persona_with_different_model_resolves_to_agent() {
        assertEquals(ActorType.AGENT, ActorTypeResolver.resolve("gpt:coder@v2"));
    }

    // Rule 5: A2A role "user" → HUMAN
    @Test
    void a2a_user_role_resolves_to_human() {
        assertEquals(ActorType.HUMAN, ActorTypeResolver.resolve("user"));
    }

    // Rule 6: A2A role "agent" → AGENT
    @Test
    void a2a_agent_role_resolves_to_agent() {
        assertEquals(ActorType.AGENT, ActorTypeResolver.resolve("agent"));
    }

    // Rule ordering guard: system:word@version must resolve via rule 2 (SYSTEM), not rule 4 (AGENT)
    @Test
    void system_versioned_persona_resolves_to_system_not_agent() {
        assertEquals(ActorType.SYSTEM, ActorTypeResolver.resolve("system:worker@v1"));
    }

    // Rule 7: everything else → HUMAN
    @Test
    void named_user_resolves_to_human() {
        assertEquals(ActorType.HUMAN, ActorTypeResolver.resolve("alice"));
    }

    @Test
    void email_user_resolves_to_human() {
        assertEquals(ActorType.HUMAN, ActorTypeResolver.resolve("alice@example.com"));
    }
}
