package io.casehub.platform.api.identity;

import java.util.List;
import java.util.Set;

public interface GroupMembershipProvider {
    Set<GroupMember> membersOf(String groupName, String tenancyId);

    default List<String> groupsOf(String actorId, String tenancyId) {
        return List.of();
    }
}
