package io.casehub.platform.api.authn;

import java.util.List;
import java.util.Set;

public interface ServiceConnectionProvider {

    ServiceConnection getConnection(String actorId, String provider, String tenancyId);

    List<ServiceConnection> listConnections(String actorId, String tenancyId);

    ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId);

    default void disconnect(String actorId, String provider, String tenancyId) {
        throw new UnsupportedOperationException("disconnect not supported");
    }

    Set<String> missingScopes(String actorId, String provider, String tenancyId);
}
