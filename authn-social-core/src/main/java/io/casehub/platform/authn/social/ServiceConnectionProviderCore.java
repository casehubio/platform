package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.ScopeRegistry;
import io.casehub.platform.api.authn.ServiceAccessToken;
import io.casehub.platform.api.authn.ServiceConnection;
import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import io.casehub.platform.api.authn.ServiceConnectionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ServiceConnectionProviderCore implements ServiceConnectionProvider {

    private final OAuthTokenStore tokenStore;
    private final OAuthTokenManagerCore tokenManager;
    private final ScopeRegistry scopeRegistry;

    public ServiceConnectionProviderCore(OAuthTokenStore tokenStore,
                                          OAuthTokenManagerCore tokenManager,
                                          ScopeRegistry scopeRegistry) {
        this.tokenStore = tokenStore;
        this.tokenManager = tokenManager;
        this.scopeRegistry = scopeRegistry;
    }

    @Override
    public ServiceConnection getConnection(String actorId, String provider, String tenancyId) {
        var record = tokenStore.findByActorId(actorId, provider, tenancyId);
        if (record.isEmpty()) {
            return new ServiceConnection(actorId, provider, tenancyId,
                ServiceConnectionStatus.DISCONNECTED, Set.of(),
                scopeRegistry.requiredScopes(provider), null);
        }
        var token = record.get();
        var missing = scopeRegistry.missingScopes(provider, token.grantedScopes());
        var status = missing.isEmpty() ? ServiceConnectionStatus.CONNECTED : ServiceConnectionStatus.PARTIAL;
        return new ServiceConnection(actorId, provider, tenancyId,
            status, token.grantedScopes(), missing, token.createdAt());
    }

    @Override
    public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
        var token = tokenManager.getValidToken(actorId, provider, tenancyId);
        if (token.isEmpty()) {
            var required = scopeRegistry.requiredScopes(provider);
            throw new ServiceConnectionException("No connection for provider: " + provider,
                provider, actorId, required, Set.of(), required);
        }
        var record = token.get();
        return new ServiceAccessToken(record.accessToken(), record.expiresAt(), record.grantedScopes());
    }

    @Override
    public List<ServiceConnection> listConnections(String actorId, String tenancyId) {
        var tokenRecords = tokenStore.findAllByActorId(actorId, tenancyId);
        var providersWithTokens = tokenRecords.stream()
            .map(OAuthTokenRecord::provider)
            .collect(Collectors.toSet());

        var connections = new ArrayList<ServiceConnection>();
        for (var record : tokenRecords) {
            connections.add(getConnection(actorId, record.provider(), tenancyId));
        }
        for (var provider : scopeRegistry.registeredProviders()) {
            if (!providersWithTokens.contains(provider)) {
                connections.add(getConnection(actorId, provider, tenancyId));
            }
        }
        return List.copyOf(connections);
    }

    @Override
    public void disconnect(String actorId, String provider, String tenancyId) {
        tokenManager.revoke(actorId, provider, tenancyId);
    }

    @Override
    public Set<String> missingScopes(String actorId, String provider, String tenancyId) {
        return getConnection(actorId, provider, tenancyId).missingScopes();
    }
}
