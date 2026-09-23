package io.casehub.platform.testing.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringInMemoryGroupMembershipProviderTest {

    private SpringInMemoryGroupMembershipProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SpringInMemoryGroupMembershipProvider();
    }

    @Test
    void addAndQueryMember() {
        provider.addMember("admins", "tenant-1", "alice");

        var members = provider.membersOf("admins", "tenant-1");
        assertThat(members).hasSize(1);
        assertThat(members.iterator().next().actorId()).isEqualTo("alice");
    }

    @Test
    void groupsOfReturnsMatchingGroups() {
        provider.addMember("admins", "tenant-1", "alice");
        provider.addMember("editors", "tenant-1", "alice");
        provider.addMember("viewers", "tenant-1", "bob");

        assertThat(provider.groupsOf("alice", "tenant-1"))
                .containsExactlyInAnyOrder("admins", "editors");
    }

    @Test
    void tenantIsolation() {
        provider.addMember("admins", "tenant-1", "alice");
        provider.addMember("admins", "tenant-2", "bob");

        assertThat(provider.membersOf("admins", "tenant-1")).hasSize(1);
        assertThat(provider.membersOf("admins", "tenant-2")).hasSize(1);
    }

    @Test
    void removeMember() {
        provider.addMember("admins", "tenant-1", "alice");
        provider.removeMember("admins", "tenant-1", "alice");

        assertThat(provider.membersOf("admins", "tenant-1")).isEmpty();
    }

    @Test
    void clearRemovesAll() {
        provider.addMember("admins", "tenant-1", "alice");
        provider.addMember("editors", "tenant-1", "bob");
        provider.clear();

        assertThat(provider.membersOf("admins", "tenant-1")).isEmpty();
        assertThat(provider.membersOf("editors", "tenant-1")).isEmpty();
    }
}
