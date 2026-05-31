package io.casehub.platform.scim;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.scim.model.ScimGroupResource;
import io.casehub.platform.scim.model.ScimListResponse;
import io.casehub.platform.scim.model.ScimMemberRef;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ScimGroupMembershipProvider implements GroupMembershipProvider {

    @Inject @RestClient ScimClient scimClient;

    @ConfigProperty(name = "casehub.platform.scim.member-page-size", defaultValue = "1000")
    int memberPageSize;

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
        List<ScimMemberRef> members = group.members();

        if (members == null || members.isEmpty() || members.size() >= memberPageSize) {
            // members absent OR possibly truncated at server page size — fetch with pagination
            members = fetchAllMembers(group.id());
        }

        if (members == null || members.isEmpty()) {
            return Set.of();
        }

        return members.stream()
            .map(m -> new GroupMember(m.value(), m.display()))
            .collect(Collectors.toUnmodifiableSet());
    }

    private List<ScimMemberRef> fetchAllMembers(String groupId) {
        List<ScimMemberRef> allMembers = new ArrayList<>();
        int startIndex = 1;
        List<ScimMemberRef> page;
        do {
            ScimGroupResource resource = scimClient.getGroup(groupId, "members", startIndex, memberPageSize);
            page = resource != null ? resource.members() : null;
            if (page == null || page.isEmpty()) break;
            allMembers.addAll(page);
            startIndex += page.size();
        } while (page.size() >= memberPageSize);
        return allMembers;
    }
}
