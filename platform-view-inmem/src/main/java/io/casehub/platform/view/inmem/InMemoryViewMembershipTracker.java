package io.casehub.platform.view.inmem;

import io.casehub.platform.api.view.ViewMembershipTracker;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryViewMembershipTracker implements ViewMembershipTracker {

    private final ConcurrentHashMap<UUID, Map<UUID, String>> state = new ConcurrentHashMap<>();

    @Override
    public Map<UUID, String> getLastKnownMembership(UUID subjectId) {
        var membership = state.get(subjectId);
        return membership != null ? new java.util.HashMap<>(membership) : Map.of();}

    @Override
    public void updateMembership(UUID subjectId, Map<UUID, String> viewIdToName) {
        state.put(subjectId, Map.copyOf(viewIdToName));
    }

    @Override
    public void removeMembership(UUID subjectId) {
        state.remove(subjectId);
    }
}
