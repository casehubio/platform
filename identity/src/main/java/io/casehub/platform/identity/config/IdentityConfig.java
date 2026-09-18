package io.casehub.platform.identity.config;

import io.casehub.platform.identity.CredentialValidationProperties;
import io.casehub.platform.identity.IdentityDIDProperties;
import io.casehub.platform.identity.ScimAgentLookupProperties;
import io.casehub.platform.identity.WebDIDResolverProperties;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.identity")
public interface IdentityConfig extends IdentityDIDProperties, CredentialValidationProperties {

    @Override
    Map<String, String> dids();

    @Override
    Map<String, String> credentials();

    @Override
    @WithDefault("1440")
    int credentialCacheTtlMinutes();

    WebConfig web();

    ScimConfig scim();

    interface WebConfig extends WebDIDResolverProperties {

        @Override
        @WithDefault("5000")
        int timeoutMs();

        @Override
        @WithDefault("1048576")
        int maxResponseBytes();
    }

    interface ScimConfig extends ScimAgentLookupProperties {

        @Override
        Optional<String> endpoint();

        @Override
        Optional<String> authToken();

        @Override
        @WithDefault("5000")
        int timeoutMs();

        @Override
        @WithDefault("5")
        int cacheTtlMinutes();

        @Override
        @WithDefault("true")
        boolean requireHttps();
    }
}
