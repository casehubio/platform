package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.AuthenticationFailure;
import io.casehub.platform.api.authn.AuthenticationProvider;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.AuthenticationSuccess;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.ChallengeStore;
import io.casehub.platform.api.authn.InvalidChallengeException;
import io.casehub.platform.api.authn.UnknownMethodException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AuthenticationRouterCore implements AuthenticationRouter {
    private static final Logger LOG = Logger.getLogger(AuthenticationRouterCore.class.getName());


    private final Map<String, AuthenticationProvider> providers;
    private final ChallengeStore                      challengeStore;
    private final AuthenticationEventListener         eventListener;
    private final ScopeMergingLoginCustomizer         scopeMergingCustomizer;

    public AuthenticationRouterCore(List<AuthenticationProvider> providers,
                                    ChallengeStore challengeStore,
                                    AuthenticationEventListener eventListener) {
        this(providers, challengeStore, eventListener, null);
    }

    public AuthenticationRouterCore(List<AuthenticationProvider> providers,
                                    ChallengeStore challengeStore,
                                    AuthenticationEventListener eventListener,
                                    ScopeMergingLoginCustomizer scopeMergingCustomizer) {
        this.providers      = providers.stream()
                                       .collect(Collectors.toUnmodifiableMap(AuthenticationProvider::method, p -> p));
        this.challengeStore = challengeStore;
        this.eventListener  = eventListener;
        this.scopeMergingCustomizer = scopeMergingCustomizer;
    }

    @Override
    public ChallengeResponse initiate(AuthenticationContext context) {
        var provider = resolveProvider(context.method());
        var effectiveContext = scopeMergingCustomizer != null
            ? scopeMergingCustomizer.customize(context, context.method())
            : context;
        var response = provider.initiate(effectiveContext);
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
        try {
            var result = provider.verify(challenge, data);
            fireEvent(() -> eventListener.onAuthenticationSuccess(new AuthenticationSuccess(
                    result.principal().id(), result.tenancyId(), result.method())));
            return result;
        } catch (RuntimeException e) {
            fireEvent(() -> eventListener.onAuthenticationFailure(new AuthenticationFailure(
                    method, e.getMessage())));
            throw e;
        }
    }

    @Override
    public Set<String> availableMethods() {
        return providers.keySet();
    }


    private static void fireEvent(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Authentication event listener failed", ex);
        }
    }

    private AuthenticationProvider resolveProvider(String method) {
        var provider = providers.get(method);
        if (provider == null) {
            throw new UnknownMethodException(method);
        }
        return provider;
    }
}
