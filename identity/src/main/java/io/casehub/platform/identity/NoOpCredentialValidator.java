package io.casehub.platform.identity;

import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.CredentialValidationResult;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/** Default no-op — DID document key check is sufficient. VC validation is opt-in. */
@ApplicationScoped
@DefaultBean
public class NoOpCredentialValidator implements AgentCredentialValidator {
    @Override
    public Optional<CredentialValidationResult> validate(final String actorId, final String did) {
        return Optional.empty();
    }
}
