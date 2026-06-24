package io.casehub.platform.testing;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.HashSet;
import java.util.Set;

/**
 * Configurable test implementation of {@link CurrentPrincipal}.
 *
 * <p>{@code @Alternative @Priority(200)} — test fixtures must beat all production
 * {@code @Alternative} implementations ({@code OidcCurrentPrincipal @Priority(100)}).
 * This bean is test-scope only; its priority has no effect in production.
 *
 * <p>Defaults to {@code actorId="system"} with empty groups, matching
 * {@code MockCurrentPrincipal} defaults so switching providers has no surprises.
 *
 * <p>All five default methods ({@code roles()}, {@code hasGroup()}, {@code actorType()},
 * {@code isSystem()}, {@code isAuthenticated()}) are inherited from the interface — nothing to override.
 *
 * <p><strong>Not thread-safe</strong> — designed for single-threaded test use only.
 * Call {@link #reset()} in a {@code @BeforeEach} method to isolate tests.
 */
@ApplicationScoped
@Alternative
@Priority(200)
public class FixedCurrentPrincipal implements CurrentPrincipal {

    private String actorId = "system";
    private Set<String> groups = new HashSet<>();
    private String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
    private boolean crossTenantAdmin = false;

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public void setGroups(Set<String> groups) {
        this.groups = new HashSet<>(groups);
    }

    public void addGroup(String group) {
        this.groups.add(group);
    }

    public void setTenancyId(String tenancyId) {
        this.tenancyId = tenancyId;
    }

    public void setCrossTenantAdmin(boolean crossTenantAdmin) {
        this.crossTenantAdmin = crossTenantAdmin;
    }

    /**
     * Resets to defaults: {@code actorId="system"}, groups empty, tenancyId to
     * {@link TenancyConstants#DEFAULT_TENANT_ID}, crossTenantAdmin false.
     * Call in {@code @BeforeEach} to isolate tests.
     */
    public void reset() {
        actorId = "system";
        groups = new HashSet<>();
        tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        crossTenantAdmin = false;
    }

    @Override
    public String actorId() { return actorId; }

    @Override
    public Set<String> groups() { return Set.copyOf(groups); }

    @Override
    public String tenancyId() { return tenancyId; }

    @Override
    public boolean isCrossTenantAdmin() { return crossTenantAdmin; }
}
