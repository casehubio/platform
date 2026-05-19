package io.casehub.platform.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryGroupMembershipProviderTest {

    private InMemoryGroupMembershipProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryGroupMembershipProvider();
    }

    @Test
    void unknown_group_returns_empty_set() {
        assertTrue(provider.membersOf("admin").isEmpty());
    }

    @Test
    void addMember_creates_group_implicitly() {
        provider.addMember("admin", "alice");
        assertTrue(provider.membersOf("admin").contains("alice"));
    }

    @Test
    void addMember_multiple_actors_to_same_group() {
        provider.addMember("admin", "alice");
        provider.addMember("admin", "bob");
        assertEquals(2, provider.membersOf("admin").size());
        assertTrue(provider.membersOf("admin").contains("alice"));
        assertTrue(provider.membersOf("admin").contains("bob"));
    }

    @Test
    void addMember_different_groups_are_independent() {
        provider.addMember("admin", "alice");
        provider.addMember("reviewer", "bob");
        assertFalse(provider.membersOf("admin").contains("bob"));
        assertFalse(provider.membersOf("reviewer").contains("alice"));
    }

    @Test
    void removeMember_removes_actor_from_group() {
        provider.addMember("admin", "alice");
        provider.addMember("admin", "bob");
        provider.removeMember("admin", "alice");
        assertFalse(provider.membersOf("admin").contains("alice"));
        assertTrue(provider.membersOf("admin").contains("bob"));
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
                () -> provider.membersOf("admin").add("hacker"));
    }
}
