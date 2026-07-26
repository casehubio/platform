package io.casehub.platform.testing;

import io.casehub.platform.api.identity.GroupMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryGroupMembershipProviderTest {

    private static final String T1 = "tenant-1";
    private static final String T2 = "tenant-2";

    private InMemoryGroupMembershipProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryGroupMembershipProvider();
    }

    private static boolean containsActorId(Iterable<GroupMember> members, String actorId) {
        for (GroupMember m : members) {
            if (m.actorId().equals(actorId)) {return true;}
        }
        return false;
    }

    @Test
    void unknown_group_returns_empty_set() {
        assertTrue(provider.membersOf("admin", T1).isEmpty());
    }

    @Test
    void addMember_creates_group_implicitly() {
        provider.addMember("admin", T1, "alice");
        assertTrue(containsActorId(provider.membersOf("admin", T1), "alice"));
    }

    @Test
    void addMember_multiple_actors_to_same_group() {
        provider.addMember("admin", T1, "alice");
        provider.addMember("admin", T1, "bob");
        assertEquals(2, provider.membersOf("admin", T1).size());
        assertTrue(containsActorId(provider.membersOf("admin", T1), "alice"));
        assertTrue(containsActorId(provider.membersOf("admin", T1), "bob"));
    }

    @Test
    void addMember_different_groups_are_independent() {
        provider.addMember("admin", T1, "alice");
        provider.addMember("reviewer", T1, "bob");
        assertFalse(containsActorId(provider.membersOf("admin", T1), "bob"));
        assertFalse(containsActorId(provider.membersOf("reviewer", T1), "alice"));
    }

    @Test
    void addMember_GroupMember_overload_preserves_displayName() {
        provider.addMember("admin", T1, new GroupMember("uuid-alice", "Alice Smith"));
        GroupMember m = provider.membersOf("admin", T1).iterator().next();
        assertEquals("uuid-alice", m.actorId());
        assertEquals("Alice Smith", m.displayName());
    }

    @Test
    void removeMember_removes_actor_from_group() {
        provider.addMember("admin", T1, "alice");
        provider.addMember("admin", T1, "bob");
        provider.removeMember("admin", T1, "alice");
        assertFalse(containsActorId(provider.membersOf("admin", T1), "alice"));
        assertTrue(containsActorId(provider.membersOf("admin", T1), "bob"));
    }

    @Test
    void removeMember_from_unknown_group_is_silent() {
        assertDoesNotThrow(() -> provider.removeMember("admin", T1, "alice"));
    }

    @Test
    void clear_removes_all_groups() {
        provider.addMember("admin", T1, "alice");
        provider.addMember("reviewer", T1, "bob");
        provider.clear();
        assertTrue(provider.membersOf("admin", T1).isEmpty());
        assertTrue(provider.membersOf("reviewer", T1).isEmpty());
    }

    @Test
    void membersOf_returns_unmodifiable_set() {
        provider.addMember("admin", T1, "alice");
        assertThrows(UnsupportedOperationException.class,
                     () -> provider.membersOf("admin", T1).add(new GroupMember("hacker", "hacker")));
    }

    @Test
    void membersOf_different_tenant_returns_empty() {
        provider.addMember("admin", T1, "alice");
        assertTrue(provider.membersOf("admin", T2).isEmpty());
    }

    @Test
    void groupsOf_returns_groups_for_tenant() {
        provider.addMember("admin", T1, "alice");
        provider.addMember("reviewer", T1, "alice");
        provider.addMember("admin", T2, "alice");
        assertEquals(List.of("admin", "reviewer"), provider.groupsOf("alice", T1).stream().sorted().toList());
        assertEquals(List.of("admin"), provider.groupsOf("alice", T2));
    }
}
