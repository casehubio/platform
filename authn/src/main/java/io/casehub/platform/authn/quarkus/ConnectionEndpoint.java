package io.casehub.platform.authn.quarkus;

import io.casehub.platform.api.authn.ConsentRequest;
import io.casehub.platform.api.authn.IncrementalConsentHandler;
import io.casehub.platform.api.authn.ScopeRegistry;
import io.casehub.platform.api.authn.ServiceConnection;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

@ApplicationScoped
@McpDomain("connections")
public class ConnectionEndpoint {

    @Inject
    ServiceConnectionProvider connectionProvider;

    @Inject
    IncrementalConsentHandler consentHandler;

    @Inject
    ScopeRegistry scopeRegistry;

    @PlatformQuery("List all service connections for the current user")
    public List<ServiceConnection> listConnections(
            @ContextParam("actorId") String actorId,
            @ContextParam("tenancyId") String tenancyId) {
        return connectionProvider.listConnections(actorId, tenancyId);
    }

    @PlatformQuery("Get a specific service connection by provider")
    public ServiceConnection getConnection(
            @PathParam String provider,
            @ContextParam("actorId") String actorId,
            @ContextParam("tenancyId") String tenancyId) {
        return connectionProvider.getConnection(actorId, provider, tenancyId);
    }

    @PlatformMutation("Request additional OAuth scopes for a provider")
    public ConsentRequest requestConsent(
            @PathParam String provider,
            Set<String> scopes,
            @ContextParam("actorId") String actorId,
            @ContextParam("tenancyId") String tenancyId) {
        return consentHandler.requestAdditionalScopes(actorId, provider, tenancyId, scopes);
    }

    @PlatformMutation("Disconnect a service connection")
    @RestMethod(HttpMethod.DELETE)
    public void disconnect(
            @PathParam String provider,
            @ContextParam("actorId") String actorId,
            @ContextParam("tenancyId") String tenancyId) {
        connectionProvider.disconnect(actorId, provider, tenancyId);
    }

    @PlatformQuery("List all registered OAuth providers")
    public Set<String> listProviders() {
        return scopeRegistry.registeredProviders();
    }
}
