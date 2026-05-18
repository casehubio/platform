package io.casehub.platform.api.identity;

import java.util.Set;

public interface GroupMembershipProvider {
    /** Returns member actor IDs for the given group. Empty set = unknown group. */
    Set<String> membersOf(String groupName);
}
