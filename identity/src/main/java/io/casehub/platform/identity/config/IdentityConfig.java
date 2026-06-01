package io.casehub.platform.identity.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;

/**
 * Platform identity configuration.
 * Config prefix: {@code casehub.identity}
 */
@ConfigMapping(prefix = "casehub.identity")
public interface IdentityConfig {

    /**
     * Maps actorId → DID URI for {@code ConfiguredActorDIDProvider}.
     * Quote keys with colons in application.properties:
     * {@code casehub.identity.dids."claude:reviewer@v1"=did:web:example.com:agents/reviewer}
     */
    Map<String, String> dids();

    /** HTTP timeout for {@code WebDIDResolver} in milliseconds (default 5000). */
    @WithDefault("5000")
    int webResolverTimeoutMs();

    /** Maximum DID document response size in bytes — SSRF/DoS protection (default 1 MiB). */
    @WithDefault("1048576")
    int webResolverMaxResponseBytes();

    /** SCIM2-based agent DID resolution configuration. */
    ScimConfig scim();

    interface ScimConfig {

        /**
         * Base URL of the SCIM2 server (e.g. {@code https://idp.example.com}).
         * Must use HTTPS — validated at first use via {@code @PostConstruct}.
         * Optional so that Quarkus config validation does not fail at startup when the
         * SCIM provider is not activated.
         */
        Optional<String> endpoint();

        /**
         * Bearer token for the {@code Authorization} header.
         * Static deploy-time credential. Optional so that Quarkus config validation
         * does not fail at startup when the SCIM provider is not activated.
         */
        Optional<String> authToken();

        /** HTTP connect + read timeout in milliseconds (default 5000). */
        @WithDefault("5000")
        int timeoutMs();

        /** TTL for cached SCIM lookups in minutes (default 5). */
        @WithDefault("5")
        int cacheTtlMinutes();

        /**
         * Whether to enforce HTTPS for the SCIM endpoint.
         * Set to {@code false} only in test environments using plain HTTP (e.g. WireMock).
         */
        @WithDefault("true")
        boolean requireHttps();
    }
}
