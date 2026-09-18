package io.casehub.platform.identity;

import java.util.Optional;

record TestScimProperties(
        Optional<String> endpoint,
        Optional<String> authToken,
        int timeoutMs,
        int cacheTtlMinutes,
        boolean requireHttps) implements ScimAgentLookupProperties {

    static TestScimProperties of(String endpoint, String authToken, int timeoutMs,
                                  int cacheTtlMinutes, boolean requireHttps) {
        return new TestScimProperties(
                Optional.ofNullable(endpoint).filter(s -> !s.isEmpty()),
                Optional.ofNullable(authToken).filter(s -> !s.isEmpty()),
                timeoutMs, cacheTtlMinutes, requireHttps);
    }
}
