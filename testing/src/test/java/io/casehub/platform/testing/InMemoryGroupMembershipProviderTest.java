package io.casehub.platform.testing;

import io.casehub.platform.api.identity.GroupMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryGroupMembershipProviderTest {

    private InMemoryGroupMembershipProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryGroupMembershipProvider();
    }

    private static boolean containsActorId(Iterable<GroupMember> members, String actorId) {
        for (GroupMember m : members) {
            if (m.actorId().equals(actorId)) return true;
        }
        return false;
    }

    @Test
    void unknown_group_returns_empty_set() {
        assertTrue(provider.membersOf("admin").isEmpty());
    }

    @Test
    void addMember_creates_group_implicitly() {
        provider.addMember("admin", "alice");
        assertTrue(containsActorId(provider.membersOf("admin"), "alice"));
    }

    @Test
    void addMember_multiple_actors_to_same_group() {
        provider.addMember("admin", "alice");
        provider.addMember("admin", "bob");
        assertEquals(2, provider.membersOf("admin").size());
        assertTrue(containsActorId(provider.membersOf("admin"), "alice"));
        assertTrue(containsActorId(provider.membersOf("admin"), "bob"));
    }

    @Test
    void addMember_different_groups_are_independent() {
        provider.addMember("admin", "alice");
        provider.addMember("reviewer", "bob");
        assertFalse(containsActorId(provider.membersOf("admin"), "bob"));
        assertFalse(containsActorId(provider.membersOf("reviewer"), "alice"));
    }

    @Test
    void addMember_GroupMember_overload_preserves_displayName() {
        provider.addMember("admin", new GroupMember("uuid-alice", "Alice Smith"));
        GroupMember m = provider.membersOf("admin").iterator().next();
        assertEquals("uuid-alice", m.actorId());
        assertEquals("Alice Smith", m.displayName());
    }

    @Test
    void removeMember_removes_actor_from_group() {
        provider.addMember("admin", "alice");
        provider.addMember("admin", "bob");
        provider.removeMember("admin", "alice");
        assertFalse(containsActorId(provider.membersOf("admin"), "alice"));
        assertTrue(containsActorId(provider.membersOf("admin"), "bob"));
    }

    @Test
    void removeMember_from_unknown_group_is_silent() {
        assertDoesNotThrow(() -> provider.removeMember("admin", "alice"));
    }

    @Test
    void clear_removes_all_groups() {
        provider.addMember("admin", "alice");
        provider.addMember("reviewer", "bob");
        provider.clear();
        assertTrue(provider.membersOf("admin").isEmpty());
        assertTrue(provider.membersOf("reviewer").isEmpty());
    }

    @Test
    void membersOf_returns_unmodifiable_set() {
        provider.addMember("admin", "alice");
        assertThrows(UnsupportedOperationException.class,
                () -> provider.membersOf("admin").add(new GroupMember("hacker", "hacker")));
    }
}
