package io.casehub.platform.testing;

import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory implementation of {@link GroupMembershipProvider} for use in tests.
 *
 * <p>Groups are created implicitly on first {@link #addMember(String, String)}.
 * Unknown groups return an empty set, consistent with the SPI contract.
 *
 * <p><strong>Not thread-safe</strong> — designed for single-threaded test use only.
 * Call {@link #clear()} in a {@code @BeforeEach} method to isolate tests.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryGroupMembershipProvider implements GroupMembershipProvider {

    private final Map<String, Set<String>> members = new HashMap<>();

    public void addMember(String groupName, String actorId) {
        members.computeIfAbsent(groupName, k -> new HashSet<>()).add(actorId);
    }

    public void removeMember(String groupName, String actorId) {
        Set<String> group = members.get(groupName);
        if (group != null) group.remove(actorId);
    }

    /**
     * Clears all group memberships. Call in {@code @BeforeEach} to isolate tests.
     */
    public void clear() {
        members.clear();
    }

    @Override
    public Set<String> membersOf(String groupName) {
        return Set.copyOf(members.getOrDefault(groupName, Set.of()));
    }
}
