package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.Set;

@Alternative
@Priority(2)
@ApplicationScoped
public class TestGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(String groupName) {
        return Set.of();
    }

    @Override
    public List<String> groupsOf(String actorId) {
        if ("actor1".equals(actorId)) return List.of("managers");
        return List.of();
    }
}
