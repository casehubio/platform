package io.casehub.platform.agent.config;

import io.casehub.platform.api.credentials.CredentialResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ManifestCredentialResolver {

    private final CredentialResolver externalResolver;

    public ManifestCredentialResolver(CredentialResolver externalResolver) {
        this.externalResolver = externalResolver;
    }

    public String resolve(CredentialRef ref) {
        return switch (ref) {
            case CredentialRef.EnvRef env -> {
                String value = System.getenv(env.variableName());
                if (value == null || value.isBlank()) {
                    throw new IllegalStateException("Environment variable not set: " + env.variableName());
                }
                yield value;
            }
            case CredentialRef.FileRef file -> {
                try {
                    yield Files.readString(Path.of(file.path())).trim();
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read credential file: " + file.path(), e);
                }
            }
            case CredentialRef.ExternalRef ext -> {
                Map<String, String> creds = externalResolver.resolve(ext.credentialRef());
                if (creds.isEmpty()) {
                    throw new IllegalStateException("Credential ref not found: " + ext.credentialRef());
                }
                yield creds.values().iterator().next();
            }
        };
    }

    public Map<String, String> resolveMap(Map<String, String> refMap) {
        var resolved = new java.util.LinkedHashMap<String, String>();
        for (var entry : refMap.entrySet()) {
            resolved.put(entry.getKey(), resolve(CredentialRef.parse(entry.getValue())));
        }
        return Map.copyOf(resolved);
    }
}
