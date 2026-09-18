package io.casehub.platform.identity;

import java.util.Optional;

public interface ScimAgentLookupProperties {

    Optional<String> endpoint();

    Optional<String> authToken();

    int timeoutMs();

    int cacheTtlMinutes();

    boolean requireHttps();
}
