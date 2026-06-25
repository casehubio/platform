package io.casehub.platform.credentials.quarkus;

import io.casehub.platform.api.credentials.CredentialResolver;
import io.quarkus.credentials.CredentialsProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Bridge from {@link CredentialResolver} to Quarkus {@link CredentialsProvider}.
 *
 * <p>{@code @Alternative @Priority(1)} displaces {@code DefaultCredentialResolver}
 * {@code @DefaultBean} when this module is on the classpath.
 *
 * <p>Uses {@code @Any Instance<CredentialsProvider>} to handle both {@code @Named}
 * and unqualified providers — direct injection would fail for {@code @Named}-only
 * beans because {@code @Named} does not carry {@code @Default} in CDI.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class QuarkusCredentialResolver implements CredentialResolver {

    @Inject
    @Any
    Instance<CredentialsProvider> providers;

    private CredentialsProvider delegate;

    @PostConstruct
    void init() {
        if (providers.isUnsatisfied()) {
            throw new IllegalStateException(
                    "No CredentialsProvider found. Add a Quarkus credential extension " +
                    "(vault, aws-secrets-manager, etc.) or remove credentials-quarkus/ from the classpath.");
        }
        if (providers.isAmbiguous()) {
            throw new IllegalStateException(
                    "Multiple CredentialsProvider beans found. This bridge requires exactly one. " +
                    "Remove extras or use CredentialsProviderFinder for multi-provider deployments.");
        }
        delegate = providers.get();
    }

    @Override
    public Map<String, String> resolve(final String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Map.of();
        }
        final Map<String, String> result = delegate.getCredentials(credentialRef);
        if (result == null || result.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(result);
    }
}
