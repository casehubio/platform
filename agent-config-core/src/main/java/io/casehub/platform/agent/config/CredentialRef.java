package io.casehub.platform.agent.config;

public sealed interface CredentialRef {

    record EnvRef(String variableName) implements CredentialRef {}

    record FileRef(String path) implements CredentialRef {}

    record ExternalRef(String credentialRef) implements CredentialRef {}

    static CredentialRef parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Credential reference must not be null or blank");
        }
        if (value.startsWith("env:")) return new EnvRef(value.substring(4));
        if (value.startsWith("file:")) return new FileRef(value.substring(5));
        if (value.startsWith("ref:")) return new ExternalRef(value.substring(4));
        throw new IllegalArgumentException(
                "Credential reference must start with env:, file:, or ref: — got: " + value);
    }
}
