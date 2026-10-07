package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ServiceAccessToken;
import io.casehub.platform.api.authn.ServiceConnection;
import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import io.casehub.platform.api.authn.ServiceConnectionStatus;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;

@DefaultBean
@ApplicationScoped
public class NoOpServiceConnectionProvider implements ServiceConnectionProvider {

    @Override
    public ServiceConnection getConnection(String actorId, String provider, String tenancyId) {
        return new ServiceConnection(actorId, provider, tenancyId,
            ServiceConnectionStatus.DISCONNECTED, Set.of(), Set.of(), null);
    }

    @Override
    public List<ServiceConnection> listConnections(String actorId, String tenancyId) {
        return List.of();
    }

    @Override
    public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
        throw new ServiceConnectionException("No service connection provider configured",
            provider, actorId, Set.of(), Set.of(), Set.of());
    }

    @Override
    public void disconnect(String actorId, String provider, String tenancyId) {
    }

    @Override
    public Set<String> missingScopes(String actorId, String provider, String tenancyId) {
        return Set.of();
    }
}
