package io.casehub.platform.agent.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProviderDeclaration(String vendor, Object credential, String host,
                                  @JsonAnySetter Map<String, String> extraProperties) {

    public ProviderDeclaration {
        extraProperties = extraProperties != null ? new LinkedHashMap<>(extraProperties) : new LinkedHashMap<>();
    }

    public ProviderDeclaration(String vendor, Object credential, String host) {
        this(vendor, credential, host, new LinkedHashMap<>());
    }

    public boolean hasScalarCredential() {
        return credential instanceof String;
    }

    public String scalarCredential() {
        if (!(credential instanceof String s)) {
            throw new IllegalStateException("Credential is not scalar for vendor " + vendor);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    public java.util.Map<String, String> mapCredential() {
        if (credential == null) {return java.util.Map.of();}
        if (!(credential instanceof java.util.Map<?, ?> m)) {
            throw new IllegalStateException("Credential is not a map for vendor " + vendor);
        }
        return (java.util.Map<String, String>) m;
    }
}
