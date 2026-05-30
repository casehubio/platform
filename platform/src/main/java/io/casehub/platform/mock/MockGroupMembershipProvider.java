package io.casehub.platform.mock;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/** @DefaultBean mock — always returns empty set. Tasks route to any available worker. */
@ApplicationScoped
@DefaultBean
public class MockGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(String groupName) {
        return Set.of();
    }
}
