package io.casehub.platform.signing.document;

import io.casehub.platform.api.signing.document.SigningProfile;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.signing")
public interface DssSigningConfig {

    Optional<String> keystorePath();

    Optional<String> keystorePassword();

    @WithDefault("PKCS12")
    String keystoreType();

    Optional<String> keyAlias();

    @WithDefault("B_T")
    SigningProfile padesProfile();

    Optional<String> tsaUrl();

    Optional<Integer> expiryWarningDays();

    Optional<String> trustedListUrl();
}
