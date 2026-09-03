package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentitySealedTest {

    private String describe(Identity identity) {
        return switch (identity) {
            case PrincipalId p -> "principal:" + p.value();
            case ActorId a -> "actor:" + a.value();
            case ParticipantId p -> "participant:" + p.value();
        };
    }

    @Test
    void exhaustive_switch_covers_all_permits() {
        assertEquals("principal:human:alice",
            describe(PrincipalId.human("alice")));
        assertEquals("actor:agent:claude",
            describe(ActorId.of(PrincipalId.agent("claude"))));
        assertEquals("participant:system:bot",
            describe(ParticipantId.of(ActorId.of(PrincipalId.system("bot")))));
    }
}
