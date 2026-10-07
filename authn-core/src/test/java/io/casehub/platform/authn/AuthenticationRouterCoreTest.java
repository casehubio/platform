package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.AuthenticationProvider;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.ChallengeStore;
import io.casehub.platform.api.authn.InvalidChallengeException;
import io.casehub.platform.api.authn.UnknownMethodException;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationRouterCoreTest {

    private final InMemChallengeStore challengeStore = new InMemChallengeStore();
    private final AuthenticationEventListener noOpListener = new AuthenticationEventListener() {};

    @Test
    void initiateDispatchesToCorrectProviderAndStoresChallenge() {
        var provider = stubProvider("webauthn", "challenge-1", Instant.now().plusSeconds(300));
        var router = new AuthenticationRouterCore(List.of(provider), challengeStore, noOpListener);

        var context = new AuthenticationContext("webauthn", "tenant-1", "https://example.com", Optional.empty(), Map.of());
        var response = router.initiate(context);

        assertThat(response.challengeId()).isEqualTo("challenge-1");
        assertThat(challengeStore.stored).containsKey("challenge-1");
    }

    @Test
    void verifyConsumesAndDelegatesToProvider() {
        var principal = PrincipalId.human("user-1");
        var provider = stubProvider("webauthn", "challenge-1", Instant.now().plusSeconds(300), principal);
        var router = new AuthenticationRouterCore(List.of(provider), challengeStore, noOpListener);

        var context = new AuthenticationContext("webauthn", "tenant-1", "https://example.com", Optional.empty(), Map.of());
        router.initiate(context);

        var result = router.verify("webauthn", "challenge-1", Map.of("attestation", "data"));
        assertThat(result.principal()).isEqualTo(principal);
        assertThat(result.method()).isEqualTo("webauthn");

        assertThat(challengeStore.stored).doesNotContainKey("challenge-1");
    }

    @Test
    void verifyThrowsOnUnknownChallenge() {
        var provider = stubProvider("webauthn", "challenge-1", Instant.now().plusSeconds(300));
        var router = new AuthenticationRouterCore(List.of(provider), challengeStore, noOpListener);

        assertThatThrownBy(() -> router.verify("webauthn", "nonexistent", Map.of()))
                .isInstanceOf(InvalidChallengeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void verifyThrowsOnExpiredChallenge() {
        var provider = stubProvider("webauthn", "challenge-1", Instant.now().minusSeconds(1));
        var router = new AuthenticationRouterCore(List.of(provider), challengeStore, noOpListener);

        var context = new AuthenticationContext("webauthn", "tenant-1", "https://example.com", Optional.empty(), Map.of());
        router.initiate(context);

        assertThatThrownBy(() -> router.verify("webauthn", "challenge-1", Map.of()))
                .isInstanceOf(InvalidChallengeException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyThrowsOnMethodMismatch() {
        var webauthn = stubProvider("webauthn", "challenge-1", Instant.now().plusSeconds(300));
        var password = stubProvider("password", "challenge-2", Instant.now().plusSeconds(300));
        var router = new AuthenticationRouterCore(List.of(webauthn, password), challengeStore, noOpListener);

        var context = new AuthenticationContext("webauthn", "tenant-1", "https://example.com", Optional.empty(), Map.of());
        router.initiate(context);

        assertThatThrownBy(() -> router.verify("password", "challenge-1", Map.of()))
                .isInstanceOf(InvalidChallengeException.class)
                .hasMessageContaining("mismatch");
    }

    @Test
    void initiateThrowsOnUnknownMethod() {
        var router = new AuthenticationRouterCore(List.of(), challengeStore, noOpListener);
        var context = new AuthenticationContext("unknown", "tenant-1", "https://example.com", Optional.empty(), Map.of());

        assertThatThrownBy(() -> router.initiate(context))
                .isInstanceOf(UnknownMethodException.class);
    }

    @Test
    void availableMethodsReturnsRegisteredMethods() {
        var webauthn = stubProvider("webauthn", "c-1", Instant.now().plusSeconds(300));
        var google = stubProvider("google", "c-2", Instant.now().plusSeconds(300));
        var router = new AuthenticationRouterCore(List.of(webauthn, google), challengeStore, noOpListener);

        assertThat(router.availableMethods()).containsExactlyInAnyOrder("webauthn", "google");
    }

    @Test
    void availableMethodsEmptyWithNoProviders() {
        var router = new AuthenticationRouterCore(List.of(), challengeStore, noOpListener);
        assertThat(router.availableMethods()).isEmpty();
    }

    @Test
    void verifyFiresAuthenticationSuccessEvent() {
        var principal = PrincipalId.human("user-1");
        var provider  = stubProvider("webauthn", "challenge-1", Instant.now().plusSeconds(300), principal);
        var capturing = new CapturingEventListener();
        var router    = new AuthenticationRouterCore(List.of(provider), challengeStore, capturing);

        var context = new AuthenticationContext("webauthn", "tenant-1", "https://example.com", Optional.empty(), Map.of());
        router.initiate(context);
        router.verify("webauthn", "challenge-1", Map.of());

        assertThat(capturing.lastSuccess).isNotNull();
        assertThat(capturing.lastSuccess.actorId()).isEqualTo("user-1");
        assertThat(capturing.lastSuccess.tenancyId()).isEqualTo("tenant-1");
        assertThat(capturing.lastSuccess.method()).isEqualTo("webauthn");
        assertThat(capturing.lastFailure).isNull();
    }

    @Test
    void verifyFiresAuthenticationFailureEventOnException() {
        var failingProvider = new FailingProvider("webauthn", "challenge-1", Instant.now().plusSeconds(300));
        var capturing       = new CapturingEventListener();
        var router          = new AuthenticationRouterCore(List.of(failingProvider), challengeStore, capturing);

        var context = new AuthenticationContext("webauthn", "tenant-1", "https://example.com", Optional.empty(), Map.of());
        router.initiate(context);

        assertThatThrownBy(() -> router.verify("webauthn", "challenge-1", Map.of()))
                .isInstanceOf(InvalidChallengeException.class);

        assertThat(capturing.lastFailure).isNotNull();
        assertThat(capturing.lastFailure.method()).isEqualTo("webauthn");
        assertThat(capturing.lastFailure.reason()).contains("bad credential");
        assertThat(capturing.lastSuccess).isNull();
    }


    private static StubProvider stubProvider(String method, String challengeId, Instant expiresAt) {
        return stubProvider(method, challengeId, expiresAt, PrincipalId.human("default-actor"));
    }

    private static StubProvider stubProvider(String method, String challengeId, Instant expiresAt, PrincipalId principal) {
        return new StubProvider(method, challengeId, expiresAt, principal);
    }

    private static class InMemChallengeStore implements ChallengeStore {
        final ConcurrentHashMap<String, ChallengeRecord> stored = new ConcurrentHashMap<>();

        @Override
        public void store(ChallengeRecord record) {
            stored.put(record.challengeId(), record);
        }

        @Override
        public Optional<ChallengeRecord> consume(String challengeId) {
            return Optional.ofNullable(stored.remove(challengeId));
        }
    }

    private record StubChallengeResponse(String challengeId, Instant expiresAt) implements ChallengeResponse {}

    private record StubProvider(String method, String challengeId, Instant expiresAt, PrincipalId principal)
            implements AuthenticationProvider {

        @Override
        public String method() { return method; }

        @Override
        public ChallengeResponse initiate(AuthenticationContext context) {
            return new StubChallengeResponse(challengeId, expiresAt);
        }

        @Override
        public AuthenticationResult verify(ChallengeRecord challenge, Map<String, Object> data) {
            return new AuthenticationResult(principal, challenge.tenancyId(), Set.of(), method, Map.of());
        }
    }

    private static class FailingProvider implements AuthenticationProvider {
        private final String  method;
        private final String  challengeId;
        private final Instant expiresAt;

        FailingProvider(String method, String challengeId, Instant expiresAt) {
            this.method      = method;
            this.challengeId = challengeId;
            this.expiresAt   = expiresAt;
        }

        @Override
        public String method() {return method;}

        @Override
        public ChallengeResponse initiate(AuthenticationContext context) {
            return new StubChallengeResponse(challengeId, expiresAt);
        }

        @Override
        public AuthenticationResult verify(ChallengeRecord challenge, Map<String, Object> data) {
            throw new InvalidChallengeException(method, "bad credential");
        }
    }


    private static class CapturingEventListener implements io.casehub.platform.api.authn.AuthenticationEventListener {
        io.casehub.platform.api.authn.AuthenticationSuccess lastSuccess;
        io.casehub.platform.api.authn.AuthenticationFailure lastFailure;

        @Override
        public void onAuthenticationSuccess(io.casehub.platform.api.authn.AuthenticationSuccess event) {
            this.lastSuccess = event;
        }

        @Override
        public void onAuthenticationFailure(io.casehub.platform.api.authn.AuthenticationFailure event) {
            this.lastFailure = event;
        }
    }

}
