package io.casehub.platform.scim;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.platform.scim")
public interface ScimConfig extends ScimProperties {
    Optional<String> token();

    @Override
    @WithDefault("1000")
    int memberPageSize();
}
