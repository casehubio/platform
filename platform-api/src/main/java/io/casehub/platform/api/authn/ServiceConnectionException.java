package io.casehub.platform.api.authn;

import java.util.Set;

public class ServiceConnectionException extends RuntimeException {
    private final String provider;
    private final String actorId;
    private final Set<String> requiredScopes;
    private final Set<String> grantedScopes;
    private final Set<String> missingScopes;

    public ServiceConnectionException(String message, String provider, String actorId,
                                       Set<String> requiredScopes, Set<String> grantedScopes,
                                       Set<String> missingScopes) {
        super(message);
        this.provider = provider;
        this.actorId = actorId;
        this.requiredScopes = requiredScopes != null ? Set.copyOf(requiredScopes) : Set.of();
        this.grantedScopes = grantedScopes != null ? Set.copyOf(grantedScopes) : Set.of();
        this.missingScopes = missingScopes != null ? Set.copyOf(missingScopes) : Set.of();
    }

    public String provider() { return provider; }
    public String actorId() { return actorId; }
    public Set<String> requiredScopes() { return requiredScopes; }
    public Set<String> grantedScopes() { return grantedScopes; }
    public Set<String> missingScopes() { return missingScopes; }
}
