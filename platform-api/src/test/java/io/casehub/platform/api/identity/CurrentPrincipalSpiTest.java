package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CurrentPrincipalSpiTest {

    private static CurrentPrincipal principal(String actorId, Set<String> groups) {
        return new CurrentPrincipal() {
            @Override public String actorId() { return actorId; }
            @Override public Set<String> groups() { return groups; }
        };
    }

    @Test
    void roles_delegates_to_groups() {
        assertEquals(Set.of("admin", "reviewer"),
            principal("alice", Set.of("admin", "reviewer")).roles());
    }

    @Test
    void hasGroup_returns_true_when_present() {
        assertTrue(principal("alice", Set.of("admin")).hasGroup("admin"));
    }

    @Test
    void hasGroup_returns_false_when_absent() {
        assertFalse(principal("alice", Set.of("admin")).hasGroup("reviewer"));
    }

    @Test
    void isSystem_returns_true_for_system_actorId() {
        assertTrue(principal("system", Set.of()).isSystem());
    }

    @Test
    void isSystem_returns_false_for_other_actorId() {
        assertFalse(principal("alice", Set.of()).isSystem());
    }

    @Test
    void isAuthenticated_returns_false_for_anonymous() {
        assertFalse(principal("anonymous", Set.of()).isAuthenticated());
    }

    @Test
    void isAuthenticated_returns_true_for_system() {
        assertTrue(principal("system", Set.of()).isAuthenticated());
    }

    @Test
    void isAuthenticated_returns_true_for_named_user() {
        assertTrue(principal("alice", Set.of()).isAuthenticated());
    }
}
