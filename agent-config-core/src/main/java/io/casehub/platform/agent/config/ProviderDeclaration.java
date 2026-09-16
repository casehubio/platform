package io.casehub.platform.agent.config;

import java.util.Map;

public record ProviderDeclaration(String vendor, Object credential, String host) {

    @SuppressWarnings("unchecked")
    public Map<String, String> credentialMap() {
        if (credential == null) return Map.of();
        if (credential instanceof String s) return Map.of("_scalar", s);
        if (credential instanceof Map<?, ?> m) return (Map<String, String>) m;
        throw new IllegalStateException("Unexpected credential type: " + credential.getClass());
    }

    public boolean hasScalarCredential() {
        return credential instanceof String;
    }
}
