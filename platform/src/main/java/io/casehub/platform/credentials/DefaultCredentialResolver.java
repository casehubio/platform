package io.casehub.platform.credentials;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MicroProfile Config-backed credential resolver.
 *
 * <p>Resolves credentials from the {@code casehub.credentials.<ref>} config namespace.
 * Supports two modes:
 * <ul>
 *   <li><b>Compound</b> — sub-keys checked first ({@code casehub.credentials.<ref>.user},
 *       {@code .password}, {@code .bearer-token}, {@code .api-key}, {@code .expires-at}).
 *       If any sub-key is found, returns the compound map.</li>
 *   <li><b>Simple</b> — bare key ({@code casehub.credentials.<ref>}) returned under
 *       {@link CredentialPropertyKeys#BEARER_TOKEN}. Used only when no sub-keys match.</li>
 * </ul>
 *
 * <p>Compound or simple, never both — if sub-keys exist, the bare key is not consulted.
 */
@DefaultBean
@ApplicationScoped
public class DefaultCredentialResolver implements CredentialResolver {

    private final Config config;

    @Inject
    DefaultCredentialResolver(final Config config) {
        this.config = config;
    }

    @Override
    public Map<String, String> resolve(final String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) return Map.of();

        final String prefix = "casehub.credentials." + credentialRef;

        final Map<String, String> compound = new LinkedHashMap<>();
        checkSubKey(prefix, CredentialPropertyKeys.USER, compound);
        checkSubKey(prefix, CredentialPropertyKeys.PASSWORD, compound);
        checkSubKey(prefix, CredentialPropertyKeys.BEARER_TOKEN, compound);
        checkSubKey(prefix, CredentialPropertyKeys.API_KEY, compound);
        checkSubKey(prefix, CredentialPropertyKeys.EXPIRES_AT, compound);
        if (!compound.isEmpty()) return Map.copyOf(compound);

        return config.getOptionalValue(prefix, String.class)
                .map(v -> Map.of(CredentialPropertyKeys.BEARER_TOKEN, v))
                .orElse(Map.of());
    }

    private void checkSubKey(final String prefix, final String key, final Map<String, String> target) {
        config.getOptionalValue(prefix + "." + key, String.class)
                .ifPresent(v -> target.put(key, v));
    }
}
