package io.casehub.platform.scim;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.platform.scim")
public interface ScimConfig {
    /** Bearer token for static auth. If absent, OIDC client "scim" is used. */
    Optional<String> token();
}
