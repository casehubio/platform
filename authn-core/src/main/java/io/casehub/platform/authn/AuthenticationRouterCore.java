package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationProvider;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.ChallengeStore;
import io.casehub.platform.api.authn.InvalidChallengeException;
import io.casehub.platform.api.authn.UnknownMethodException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthenticationRouterCore implements AuthenticationRouter {

    private final Map<String, AuthenticationProvider> providers;
    private final ChallengeStore challengeStore;

    public AuthenticationRouterCore(List<AuthenticationProvider> providers, ChallengeStore challengeStore) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(AuthenticationProvider::method, p -> p));
        this.challengeStore = challengeStore;
    }

    @Override
    public ChallengeResponse initiate(AuthenticationContext context) {
        var provider = resolveProvider(context.method());
        var response = provider.initiate(context);
        var record = new ChallengeRecord(
                response.challengeId(), context.method(), context.tenancyId(),
                null, Instant.now(), response.expiresAt());
        challengeStore.store(record);
        return response;
    }

    @Override
    public AuthenticationResult verify(String method, String challengeId, Map<String, Object> data) {
        var challenge = challengeStore.consume(challengeId)
                .orElseThrow(() -> new InvalidChallengeException(method, "Challenge not found: " + challengeId));
        if (challenge.expiresAt() != null && challenge.expiresAt().isBefore(Instant.now())) {
            throw new InvalidChallengeException(method, "Challenge expired: " + challengeId);
        }
        if (!challenge.method().equals(method)) {
            throw new InvalidChallengeException(method,
                    "Challenge method mismatch: expected " + method + " but challenge was issued for " + challenge.method());
        }
        var provider = resolveProvider(challenge.method());
        return provider.verify(challenge, data);
    }

    @Override
    public Set<String> availableMethods() {
        return providers.keySet();
    }

    private AuthenticationProvider resolveProvider(String method) {
        var provider = providers.get(method);
        if (provider == null) {
            throw new UnknownMethodException(method);
        }
        return provider;
    }
}
