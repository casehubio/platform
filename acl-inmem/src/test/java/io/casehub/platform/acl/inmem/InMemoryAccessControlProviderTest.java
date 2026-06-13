package io.casehub.platform.acl.inmem;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AccessControlProviderContractTest;
import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;

import java.util.List;
import java.util.Set;

class InMemoryAccessControlProviderTest extends AccessControlProviderContractTest {

    private final GroupMembershipProvider groupMembership = new GroupMembershipProvider() {
        @Override
        public Set<GroupMember> membersOf(String groupName) {
            return Set.of();
        }

        @Override
        public List<String> groupsOf(String actorId) {
            if ("actor1".equals(actorId)) return List.of("managers");
            return List.of();
        }
    };

    private InMemoryAccessControlProvider provider;

    @Override
    protected AccessControlProvider provider() {
        return provider;
    }

    @Override
    protected GroupMembershipProvider groupMembership() {
        return groupMembership;
    }

    @Override
    protected void clearState() {
        provider = new InMemoryAccessControlProvider(groupMembership);
    }
}
