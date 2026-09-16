package io.casehub.platform.agent.config;

public record ProviderDeclaration(String vendor, Object credential, String host) {

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
