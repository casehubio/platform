package io.casehub.platform.api.authn;

import java.util.Objects;
import java.util.Set;

public class InsufficientScopesException extends RuntimeException {

    private final String provider;
    private final String actorId;
    private final Set<String> requiredScopes;
    private final Set<String> grantedScopes;
    private final Set<String> missingScopes;

    public InsufficientScopesException(
            String provider, String actorId,
            Set<String> requiredScopes, Set<String> grantedScopes,
            Set<String> missingScopes) {
        super(Objects.requireNonNull(provider, "provider")
              + ": missing scopes " + missingScopes + ", granted " + grantedScopes);
        this.provider = provider;
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.requiredScopes = Set.copyOf(Objects.requireNonNull(requiredScopes, "requiredScopes"));
        this.grantedScopes = Set.copyOf(Objects.requireNonNull(grantedScopes, "grantedScopes"));
        this.missingScopes = Set.copyOf(Objects.requireNonNull(missingScopes, "missingScopes"));
    }

    public String getProvider() { return provider; }
    public String getActorId() { return actorId; }
    public Set<String> getRequiredScopes() { return requiredScopes; }
    public Set<String> getGrantedScopes() { return grantedScopes; }
    public Set<String> getMissingScopes() { return missingScopes; }
}
