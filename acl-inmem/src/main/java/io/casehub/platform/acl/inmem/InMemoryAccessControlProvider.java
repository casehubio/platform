package io.casehub.platform.acl.inmem;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntry;
import io.casehub.platform.api.acl.AclEntryType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(10)
@ApplicationScoped
public class InMemoryAccessControlProvider implements AccessControlProvider {

    private final ConcurrentHashMap<GrantKey, AclEntry> grants  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GrantKey, AclEntry> denies  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ParentKey, String>  parents = new ConcurrentHashMap<>();
    private final GroupMembershipProvider               groupMembership;
    private final CurrentPrincipal                      principal;

    @Inject
    public InMemoryAccessControlProvider(GroupMembershipProvider groupMembership, CurrentPrincipal principal) {
        this.groupMembership = groupMembership;
        this.principal       = principal;
    }

    @Override
    public boolean canAccess(String actorId, String resourceId, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        return resolveAccess(candidates, resourceId, action, 0);
    }

    @Override
    public void grant(String actorId, String resourceId, AclAction action, Instant expires) {
        String tenancyId = principal.tenancyId();
        var    key       = new GrantKey(actorId, resourceId, action, tenancyId);
        grants.put(key, new AclEntry(actorId, resourceId, action, AclEntryType.ALLOW, Instant.now(), expires, tenancyId));
    }

    @Override
    public void deny(String actorId, String resourceId, AclAction action, Instant expires) {
        String tenancyId = principal.tenancyId();
        var    key       = new GrantKey(actorId, resourceId, action, tenancyId);
        denies.put(key, new AclEntry(actorId, resourceId, action, AclEntryType.DENY, Instant.now(), expires, tenancyId));
    }

    @Override
    public void revoke(String actorId, String resourceId, AclAction action) {
        String tenancyId = principal.tenancyId();
        grants.remove(new GrantKey(actorId, resourceId, action, tenancyId));
    }

    @Override
    public void removeDeny(String actorId, String resourceId, AclAction action) {
        String tenancyId = principal.tenancyId();
        denies.remove(new GrantKey(actorId, resourceId, action, tenancyId));
    }

    @Override
    public void revokeAll(String actorId, String resourceId) {
        String tenancyId = principal.tenancyId();
        for (AclAction action : AclAction.values()) {
            grants.remove(new GrantKey(actorId, resourceId, action, tenancyId));
            denies.remove(new GrantKey(actorId, resourceId, action, tenancyId));
        }
    }

    @Override
    public void registerParent(String childResourceId, String parentResourceId) {
        parents.put(new ParentKey(childResourceId, principal.tenancyId()), parentResourceId);
    }

    @Override
    public List<String> accessibleResources(String actorId, String resourceType, AclAction action) {
        Set<String>    candidates    = buildCandidateSet(actorId);
        String         prefix        = resourceType + ":";
        boolean        filterTenant  = shouldFilterByTenant();
        String         tenancyId     = principal.tenancyId();
        Set<AclAction> satisfyingSet = action.satisfiedBy();
        Set<AclAction> deniedBySet   = action.deniedBy();
        String         wildcardId    = resourceType + ":*";

        Set<String> seen = new LinkedHashSet<>();
        for (var entry : grants.values()) {
            if (candidates.contains(entry.actorId())
                && satisfyingSet.contains(entry.action())
                && entry.resourceId().startsWith(prefix)
                && !entry.isExpired()
                && (!filterTenant || tenancyId.equals(entry.tenancyId()))) {
                seen.add(entry.resourceId());
            }
        }

        seen.removeIf(resourceId -> isDenied(candidates, resourceId, deniedBySet, filterTenant, tenancyId));

        return new ArrayList<>(seen);
    }

    @Override
    public List<String> accessibleResourcesIncludingInherited(String actorId, String resourceType, AclAction action) {
        Set<String>    candidates    = buildCandidateSet(actorId);
        String         prefix        = resourceType + ":";
        boolean        filterTenant  = shouldFilterByTenant();
        String         tenancyId     = principal.tenancyId();
        Set<AclAction> satisfyingSet = action.satisfiedBy();
        Set<AclAction> deniedBySet   = action.deniedBy();

        Set<String> directlyGranted = new LinkedHashSet<>();
        for (var entry : grants.values()) {
            if (candidates.contains(entry.actorId())
                && satisfyingSet.contains(entry.action())
                && !entry.isExpired()
                && (!filterTenant || tenancyId.equals(entry.tenancyId()))) {
                directlyGranted.add(entry.resourceId());
            }
        }

        Set<String> result = new LinkedHashSet<>();
        for (String resourceId : directlyGranted) {
            if (resourceId.startsWith(prefix) && !isDenied(candidates, resourceId, deniedBySet, filterTenant, tenancyId)) {
                result.add(resourceId);
            }
            collectChildren(resourceId, prefix, tenancyId, filterTenant, result, candidates, deniedBySet, 0);
        }

        return new ArrayList<>(result);
    }

    private void collectChildren(String parentResourceId, String prefix, String tenancyId,
                                 boolean filterTenant, Set<String> result, Set<String> candidates,
                                 Set<AclAction> deniedBySet, int depth) {
        if (depth > 20) {return;}
        for (var entry : parents.entrySet()) {
            ParentKey key = entry.getKey();
            if (entry.getValue().equals(parentResourceId)
                && (!filterTenant || tenancyId.equals(key.tenancyId()))) {
                String childId = key.childResourceId();
                if (childId.startsWith(prefix) && !isDenied(candidates, childId, deniedBySet, filterTenant, tenancyId)) {
                    result.add(childId);
                }
                collectChildren(childId, prefix, tenancyId, filterTenant, result, candidates, deniedBySet, depth + 1);
            }
        }
    }

    private Set<String> buildCandidateSet(String actorId) {
        Set<String> candidates = new HashSet<>();
        candidates.add(actorId);
        for (String group : groupMembership.groupsOf(actorId, principal.tenancyId())) {
            candidates.add("group:" + group);
        }
        return candidates;
    }

    private boolean resolveAccess(Set<String> candidates, String resourceId,
                                  AclAction action, int depth) {
        if (depth > 20) {return false;}

        // resolveAt: check deny/grant at instance then wildcard level
        int resolution = resolveAt(candidates, resourceId, action);
        if (resolution != 0) {return resolution > 0;}

        // walk parent chain
        String tenancyId = principal.tenancyId();
        String parent    = parents.get(new ParentKey(resourceId, tenancyId));
        if (parent != null) {
            return resolveAccess(candidates, parent, action, depth + 1);
        }

        return false;
    }

    /**
     * @return 1 = ALLOW, -1 = DENY, 0 = CONTINUE
     */
    private int resolveAt(Set<String> candidates, String resourceId, AclAction action) {
        boolean        filterTenant  = shouldFilterByTenant();
        String         tenancyId     = principal.tenancyId();
        Set<AclAction> deniedBySet   = action.deniedBy();
        Set<AclAction> satisfyingSet = action.satisfiedBy();

        // 1. Instance deny
        if (isDenied(candidates, resourceId, deniedBySet, filterTenant, tenancyId)) {return -1;}

        // 2. Instance grant
        if (isGranted(candidates, resourceId, satisfyingSet, filterTenant, tenancyId)) {return 1;}

        // 3-4. Wildcard deny/grant
        int colonIndex = resourceId.indexOf(':');
        if (colonIndex > 0) {
            String wildcardId = resourceId.substring(0, colonIndex) + ":*";
            if (!wildcardId.equals(resourceId)) {
                // 3. Wildcard deny
                if (isDenied(candidates, wildcardId, deniedBySet, filterTenant, tenancyId)) {return -1;}
                // 4. Wildcard grant
                if (isGranted(candidates, wildcardId, satisfyingSet, filterTenant, tenancyId)) {return 1;}
            }
        }

        return 0; // CONTINUE
    }

    private boolean isDenied(Set<String> candidates, String resourceId, Set<AclAction> deniedBySet,
                             boolean filterTenant, String tenancyId) {
        for (String candidate : candidates) {
            for (AclAction denyAction : deniedBySet) {
                GrantKey key = new GrantKey(candidate, resourceId, denyAction, tenancyId);
                if (filterTenant) {
                    AclEntry entry = denies.get(key);
                    if (entry != null && !entry.isExpired()) {return true;}
                } else {
                    for (var e : denies.entrySet()) {
                        var k = e.getKey();
                        if (k.actorId().equals(candidate) && k.resourceId().equals(resourceId)
                            && k.action() == denyAction && e.getValue() != null && !e.getValue().isExpired()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isGranted(Set<String> candidates, String resourceId, Set<AclAction> satisfyingSet,
                              boolean filterTenant, String tenancyId) {
        for (String candidate : candidates) {
            for (AclAction satisfying : satisfyingSet) {
                if (filterTenant) {
                    AclEntry entry = grants.get(new GrantKey(candidate, resourceId, satisfying, tenancyId));
                    if (entry != null && !entry.isExpired()) {return true;}
                } else {
                    for (var e : grants.entrySet()) {
                        var k = e.getKey();
                        if (k.actorId().equals(candidate) && k.resourceId().equals(resourceId)
                            && k.action() == satisfying && e.getValue() != null && !e.getValue().isExpired()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean shouldFilterByTenant() {
        return !principal.isCrossTenantAdmin();
    }

    private record GrantKey(String actorId, String resourceId, AclAction action, String tenancyId) {}

    private record ParentKey(String childResourceId, String tenancyId) {}
}
