package io.casehub.platform.scim;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.platform.scim")
public interface ScimConfig {
    /** Bearer token for static auth. If absent, OIDC client "scim" is used. */
    Optional<String> token();

    /** Number of members fetched per SCIM page when paginating group members. */
    @WithDefault("1000")
    int memberPageSize();
}
