package io.casehub.platform.scim;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.scim.model.ScimGroupResource;
import io.casehub.platform.scim.model.ScimListResponse;
import io.casehub.platform.scim.model.ScimMemberRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ScimGroupMembershipProviderCore {

    private final ScimClient scimClient;
    private final ScimProperties properties;

    public ScimGroupMembershipProviderCore(ScimClient scimClient, ScimProperties properties) {
        this.scimClient = scimClient;
        this.properties = properties;
    }

    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        String safeGroupName = groupName.replace("\\", "\\\\").replace("\"", "\\\"");
        String filter = "displayName eq \"" + safeGroupName + "\"";
        ScimListResponse<ScimGroupResource> response =
                scimClient.listGroups(filter, "id,displayName,members");

        if (response.resources() == null || response.resources().isEmpty()) {
            return Set.of();
        }

        ScimGroupResource group = response.resources().get(0);
        List<ScimMemberRef> members = group.members();

        if (members == null || members.isEmpty() || members.size() >= properties.memberPageSize()) {
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
            ScimGroupResource resource = scimClient.getGroup(
                    groupId, "members", startIndex, properties.memberPageSize());
            page = resource != null ? resource.members() : null;
            if (page == null || page.isEmpty()) break;
            allMembers.addAll(page);
            startIndex += page.size();
        } while (page.size() >= properties.memberPageSize());
        return allMembers;
    }
}
