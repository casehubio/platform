package io.casehub.platform.scim;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.scim.model.ScimGroupResource;
import io.casehub.platform.scim.model.ScimListResponse;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ScimGroupMembershipProvider implements GroupMembershipProvider {

    @Inject @RestClient ScimClient scimClient;

    @Override
    @CacheResult(cacheName = "scim-group-members")
    public Set<GroupMember> membersOf(String groupName) {
        String safeGroupName = groupName.replace("\\", "\\\\").replace("\"", "\\\"");
        String filter = "displayName eq \"" + safeGroupName + "\"";
        ScimListResponse<ScimGroupResource> response =
            scimClient.listGroups(filter, "id,displayName,members");

        if (response.resources() == null || response.resources().isEmpty()) {
            return Set.of();
        }

        ScimGroupResource group = response.resources().get(0);
        List<io.casehub.platform.scim.model.ScimMemberRef> members = group.members();

        if (members == null || members.isEmpty()) {
            // Step 2: members absent from list response — fetch group directly
            ScimGroupResource fullGroup = scimClient.getGroup(group.id(), "members");
            members = fullGroup != null ? fullGroup.members() : null;
        }

        if (members == null || members.isEmpty()) {
            return Set.of();
        }

        return members.stream()
            .map(m -> new GroupMember(m.value(), m.display()))
            .collect(Collectors.toUnmodifiableSet());
    }
}
