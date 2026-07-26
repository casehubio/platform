package io.casehub.platform.testing;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryGroupMembershipProvider implements GroupMembershipProvider {

    private final Map<String, Set<GroupMember>> members = new HashMap<>();

    public void addMember(String groupName, String tenancyId, String actorId) {
        addMember(groupName, tenancyId, new GroupMember(actorId, actorId));
    }

    public void addMember(String groupName, String tenancyId, GroupMember member) {
        String key = tenancyId + "::" + groupName;
        members.computeIfAbsent(key, k -> new HashSet<>()).add(member);
    }

    public void removeMember(String groupName, String tenancyId, String actorId) {
        String           key   = tenancyId + "::" + groupName;
        Set<GroupMember> group = members.get(key);
        if (group != null) {group.removeIf(m -> m.actorId().equals(actorId));}
    }

    public void clear() {
        members.clear();
    }

    @Override
    public Set<GroupMember> membersOf(String groupName, String tenancyId) {
        String key = tenancyId + "::" + groupName;
        return Set.copyOf(members.getOrDefault(key, Set.of()));
    }

    @Override
    public List<String> groupsOf(String actorId, String tenancyId) {
        String       prefix = tenancyId + "::";
        List<String> result = new ArrayList<>();
        for (var entry : members.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().stream().anyMatch(m -> m.actorId().equals(actorId))) {
                result.add(entry.getKey().substring(prefix.length()));
            }
        }
        return result;
    }
}
