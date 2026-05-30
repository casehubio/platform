package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupMembershipProviderSpiTest {

    private GroupMembershipProvider provider(Set<GroupMember> members) {
        return groupName -> members;
    }

    @Test
    void membersOf_returns_set_of_GroupMember_not_strings() {
        var alice = new GroupMember("alice-uuid", "Alice");
        var provider = provider(Set.of(alice));
        Set<GroupMember> result = provider.membersOf("reviewers");
        assertTrue(result.contains(alice));
    }

    @Test
    void membersOf_returns_empty_set_for_unknown_group() {
        var provider = provider(Set.of());
        assertEquals(Set.of(), provider.membersOf("unknown"));
    }

    @Test
    void actorId_not_displayName_is_the_stable_identity_key() {
        var member = new GroupMember("uuid-stable", "Display That Might Change");
        var provider = provider(Set.of(member));
        var result = provider.membersOf("g").iterator().next();
        assertEquals("uuid-stable", result.actorId());
    }
}
