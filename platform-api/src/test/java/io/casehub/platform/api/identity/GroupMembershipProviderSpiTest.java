package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupMembershipProviderSpiTest {

    private final GroupMembershipProvider spi = new GroupMembershipProvider() {
        @Override
        public Set<GroupMember> membersOf(String groupName) {
            return Set.of();
        }
    };

    @Test
    void groupsOf_defaultReturnsEmptyList() {
        assertTrue(spi.groupsOf("actor1").isEmpty());
    }
}
