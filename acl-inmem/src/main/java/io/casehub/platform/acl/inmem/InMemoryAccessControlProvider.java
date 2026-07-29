package io.casehub.platform.acl.inmem;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntry;
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
        return canAccessWithCandidates(candidates, resourceId, action, 0);
    }

    @Override
    public void grant(String actorId, String resourceId, AclAction action, Instant expires) {
        String tenancyId = principal.tenancyId();
        var    key       = new GrantKey(actorId, resourceId, action, tenancyId);
        grants.put(key, new AclEntry(actorId, resourceId, action, Instant.now(), expires, tenancyId));
    }

    @Override
    public void revoke(String actorId, String resourceId, AclAction action) {
        String tenancyId = principal.tenancyId();
        grants.remove(new GrantKey(actorId, resourceId, action, tenancyId));
    }

    @Override
    public void revokeAll(String actorId, String resourceId) {
        String tenancyId = principal.tenancyId();
        for (AclAction action : AclAction.values()) {
            grants.remove(new GrantKey(actorId, resourceId, action, tenancyId));
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
        Set<String>    seen          = new LinkedHashSet<>();
        for (var entry : grants.values()) {
            if (candidates.contains(entry.actorId())
                && satisfyingSet.contains(entry.action())
                && entry.resourceId().startsWith(prefix)
                && !entry.isExpired()
                && (!filterTenant || tenancyId.equals(entry.tenancyId()))) {
                seen.add(entry.resourceId());
            }
        }
        return new ArrayList<>(seen);}

    private Set<String> buildCandidateSet(String actorId) {
        Set<String> candidates = new HashSet<>();
        candidates.add(actorId);
        for (String group : groupMembership.groupsOf(actorId, principal.tenancyId())) {
            candidates.add("group:" + group);
        }
        return candidates;
    }

    private boolean canAccessWithCandidates(Set<String> candidates, String resourceId,
                                            AclAction action, int depth) {
        if (depth > 20) {return false;}
        boolean filterTenant = shouldFilterByTenant();
        String  tenancyId    = principal.tenancyId();
        for (String candidate : candidates) {
            for (AclAction satisfying : action.satisfiedBy()) {
                if (filterTenant) {
                    AclEntry entry = grants.get(new GrantKey(candidate, resourceId, satisfying, tenancyId));
                    if (entry != null && !entry.isExpired()) {return true;}
                } else {
                    for (var entry : grants.entrySet()) {
                        var k = entry.getKey();
                        if (k.actorId().equals(candidate) && k.resourceId().equals(resourceId)
                            && k.action() == satisfying && entry.getValue() != null && !entry.getValue().isExpired()) {
                            return true;
                        }
                    }
                }
            }
        }
        String parent = parents.get(new ParentKey(resourceId, tenancyId));
        if (parent != null) {
            return canAccessWithCandidates(candidates, parent, action, depth + 1);
        }
        return false;}

    private boolean shouldFilterByTenant() {
        return !principal.isCrossTenantAdmin();
    }

    private record GrantKey(String actorId, String resourceId, AclAction action, String tenancyId) {}

    private record ParentKey(String childResourceId, String tenancyId) {}
}
