package io.casehub.platform.api.identity;

import java.util.Set;

public interface GroupMembershipProvider {
    /** Returns members for the given group name. Empty set = group unknown or has no members. */
    Set<GroupMember> membersOf(String groupName);
}
