package io.casehub.platform.testing;

import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.HashSet;
import java.util.Set;

/**
 * Configurable test implementation of {@link CurrentPrincipal}.
 *
 * <p>Defaults to {@code actorId="system"} with empty groups, matching
 * {@code MockCurrentPrincipal} defaults so switching providers has no surprises.
 *
 * <p>All four default methods ({@code roles()}, {@code hasGroup()}, {@code isSystem()},
 * {@code isAuthenticated()}) are inherited from the interface — nothing to override.
 *
 * <p><strong>Not thread-safe</strong> — designed for single-threaded test use only.
 * Call {@link #reset()} in a {@code @BeforeEach} method to isolate tests.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class FixedCurrentPrincipal implements CurrentPrincipal {

    private String actorId = "system";
    private Set<String> groups = new HashSet<>();

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public void setGroups(Set<String> groups) {
        this.groups = new HashSet<>(groups);
    }

    public void addGroup(String group) {
        this.groups.add(group);
    }

    /**
     * Resets to defaults: {@code actorId="system"}, groups empty.
     * Call in {@code @BeforeEach} to isolate tests.
     */
    public void reset() {
        actorId = "system";
        groups = new HashSet<>();
    }

    @Override
    public String actorId() {
        return actorId;
    }

    @Override
    public Set<String> groups() {
        return Set.copyOf(groups);
    }
}
