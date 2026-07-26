package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Set;

@Alternative
@Priority(300)
@ApplicationScoped
public class TestCurrentPrincipal implements CurrentPrincipal {

    private volatile String tenancyId = "test-tenant";

    @Override public String actorId() { return "system"; }
    @Override public Set<String> groups() { return Set.of(); }
    @Override public boolean isSystem() { return true; }
    @Override public boolean isAuthenticated() { return true; }
    @Override public String tenancyId() { return tenancyId; }
    @Override public boolean isCrossTenantAdmin() { return false; }

    public void setTenancyId(String tenancyId) {
        this.tenancyId = tenancyId;
    }
}