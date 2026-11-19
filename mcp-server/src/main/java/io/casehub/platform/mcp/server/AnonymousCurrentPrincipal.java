package io.casehub.platform.mcp.server;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@DefaultBean
@ApplicationScoped
public class AnonymousCurrentPrincipal implements CurrentPrincipal {

    @Override public String actorId() { return "anonymous"; }
    @Override public Set<String> groups() { return Set.of(); }
    @Override public String tenancyId() { return TenancyConstants.DEFAULT_TENANT_ID; }
    @Override public boolean isCrossTenantAdmin() { return false; }
}
