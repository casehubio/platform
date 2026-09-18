package io.casehub.platform.identity;

import java.util.Map;

public interface CredentialValidationProperties {

    Map<String, String> credentials();

    int credentialCacheTtlMinutes();
}
