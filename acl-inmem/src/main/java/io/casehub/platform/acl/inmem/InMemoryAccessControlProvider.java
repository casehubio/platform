package io.casehub.platform.acl.inmem;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntry;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(10)
@ApplicationScoped
public class InMemoryAccessControlProvider implements AccessControlProvider {

    private final ConcurrentHashMap<GrantKey, AclEntry> grants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> parents = new ConcurrentHashMap<>();
    private final GroupMembershipProvider groupMembership;

    @Inject
    public InMemoryAccessControlProvider(GroupMembershipProvider groupMembership) {
        this.groupMembership = groupMembership;
    }

    @Override
    public CompletionStage<Boolean> canAccess(String actorId, String resourceId, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        return CompletableFuture.completedFuture(canAccessWithCandidates(candidates, resourceId, action, 0));
    }

    @Override
    public CompletionStage<Void> grant(String actorId, String resourceId, AclAction action, Instant expires) {
        var key = new GrantKey(actorId, resourceId, action);
        grants.put(key, new AclEntry(actorId, resourceId, action, Instant.now(), expires, ""));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> revoke(String actorId, String resourceId, AclAction action) {
        grants.remove(new GrantKey(actorId, resourceId, action));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> revokeAll(String actorId, String resourceId) {
        for (AclAction action : AclAction.values()) {
            grants.remove(new GrantKey(actorId, resourceId, action));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> registerParent(String childResourceId, String parentResourceId) {
        parents.put(childResourceId, parentResourceId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<String>> accessibleResources(String actorId, String resourceType, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        String prefix = resourceType + ":";
        List<String> result = new ArrayList<>();
        for (var entry : grants.values()) {
            if (candidates.contains(entry.actorId())
                    && entry.action() == action
                    && entry.resourceId().startsWith(prefix)
                    && !entry.isExpired()) {
                result.add(entry.resourceId());
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    private Set<String> buildCandidateSet(String actorId) {
        Set<String> candidates = new HashSet<>();
        candidates.add(actorId);
        for (String group : groupMembership.groupsOf(actorId)) {
            candidates.add("group:" + group);
        }
        return candidates;
    }

    private boolean canAccessWithCandidates(Set<String> candidates, String resourceId,
                                            AclAction action, int depth) {
        if (depth > 20) return false;
        for (String candidate : candidates) {
            AclEntry entry = grants.get(new GrantKey(candidate, resourceId, action));
            if (entry != null && !entry.isExpired()) return true;
        }
        String parent = parents.get(resourceId);
        if (parent != null) {
            return canAccessWithCandidates(candidates, parent, action, depth + 1);
        }
        return false;
    }

    private record GrantKey(String actorId, String resourceId, AclAction action) {}
}
