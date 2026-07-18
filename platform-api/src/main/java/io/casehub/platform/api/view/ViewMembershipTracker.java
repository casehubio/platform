package io.casehub.platform.api.view;

import java.util.Map;
import java.util.UUID;

public interface ViewMembershipTracker {
    Map<UUID, String> getLastKnownMembership(UUID subjectId);
    void updateMembership(UUID subjectId, Map<UUID, String> viewIdToName);
    void removeMembership(UUID subjectId);
}
