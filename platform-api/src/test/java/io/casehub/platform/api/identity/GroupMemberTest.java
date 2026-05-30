package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupMemberTest {

    @Test
    void actorId_is_the_stable_scim_value_uuid() {
        var m = new GroupMember("user-uuid-123", "Alice Smith");
        assertEquals("user-uuid-123", m.actorId());
    }

    @Test
    void displayName_is_the_human_label() {
        var m = new GroupMember("user-uuid-123", "Alice Smith");
        assertEquals("Alice Smith", m.displayName());
    }

    @Test
    void equality_by_both_fields() {
        var a = new GroupMember("id-1", "Alice");
        var b = new GroupMember("id-1", "Alice");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void members_collected_into_set_by_actorId() {
        var members = Set.of(
            new GroupMember("id-alice", "Alice"),
            new GroupMember("id-bob", "Bob")
        );
        assertTrue(members.stream().anyMatch(m -> m.actorId().equals("id-alice")));
    }
}
