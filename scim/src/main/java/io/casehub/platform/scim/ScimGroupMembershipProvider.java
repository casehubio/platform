package io.casehub.platform.scim;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class ScimGroupMembershipProvider implements GroupMembershipProvider {

    @Inject ScimGroupMembershipProviderCore delegate;

    @Override
    @CacheResult(cacheName = "scim-group-members")
    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        return delegate.membersOf(groupName, tenancyId);
    }
}
