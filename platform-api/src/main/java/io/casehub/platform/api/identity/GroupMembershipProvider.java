package io.casehub.platform.api.identity;

import java.util.List;
import java.util.Set;

public interface GroupMembershipProvider {
    /** Returns members for the given group name. Empty set = group unknown or has no members. */
    Set<GroupMember> membersOf(String groupName);

    /** Returns the groups that actorId belongs to. Empty list = actor has no groups. */
    default List<String> groupsOf(String actorId) {
        return List.of();
    }
}
