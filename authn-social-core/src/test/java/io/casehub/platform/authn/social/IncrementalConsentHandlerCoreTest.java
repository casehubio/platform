package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.ProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalConsentHandlerCoreTest {

    @Test
    void requestAdditionalScopesReturnsConsentRequest() {
        var provider = createStubProvider("google",
                "https://accounts.google.com/o/oauth2/auth");

        var handler = new IncrementalConsentHandlerCore(
                Map.of("google", provider));

        var result = handler.requestAdditionalScopes(
                "actor1", "google", "tenant1",
                Set.of("calendar.readonly"));

        assertEquals("google", result.provider());
        assertEquals(Set.of("calendar.readonly"), result.requestedScopes());
        assertNotNull(result.authorizationUrl());
        assertNotNull(result.state());
    }

    @Test
    void requestForUnknownProviderThrows() {
        var handler = new IncrementalConsentHandlerCore(Map.of());

        var ex = assertThrows(ProviderUnavailableException.class, () ->
                handler.requestAdditionalScopes(
                        "actor1", "unknown", "tenant1",
                        Set.of("some.scope")));
        assertEquals("unknown", ex.method());
    }

    @Test
    void requestPassesAdditionalScopesViaHints() {
        var capturingProvider = new CapturingProvider("github");

        var handler = new IncrementalConsentHandlerCore(
                Map.of("github", capturingProvider));

        handler.requestAdditionalScopes(
                "actor1", "github", "tenant1",
                Set.of("repo", "user:email"));

        assertNotNull(capturingProvider.lastContext);
        var hints = capturingProvider.lastContext.hints();
        assertTrue(hints.containsKey("additionalScopes"));
        @SuppressWarnings("unchecked")
        var scopes = (Set<String>) hints.get("additionalScopes");
        assertTrue(scopes.contains("repo"));
        assertTrue(scopes.contains("user:email"));
    }

    private static AbstractOAuthAuthenticationProvider createStubProvider(String method, String authUrl) {
        return new TestProvider(method, authUrl, false);
    }

    private static class CapturingProvider extends TestProvider {
        AuthenticationContext lastContext;

        CapturingProvider(String method) {
            super(method, "https://example.com/auth", true);
        }

        @Override
        public ChallengeResponse initiate(AuthenticationContext context) {
            this.lastContext = context;
            return super.initiate(context);
        }
    }

    private static class TestProvider extends AbstractOAuthAuthenticationProvider {
        private final String method;
        private final String authUrl;
        private final boolean useRealInitiate;

        TestProvider(String method, String authUrl, boolean useRealInitiate) {
            super(new StubOAuthConfig(), new StubOAuthHttpClient(),
                    new StubOAuthTokenStore(), new StubIdentityBindingStore(),
                    (email, tenancyId) -> Optional.empty());
            this.method = method;
            this.authUrl = authUrl;
            this.useRealInitiate = useRealInitiate;
        }

        @Override public String method() { return method; }
        @Override protected String authorizationEndpoint() { return authUrl; }
        @Override protected String tokenEndpoint() { return "https://example.com/token"; }
        @Override protected OAuthIdentity extractIdentity(OAuthTokenResponse r) { return null; }

        @Override
        public ChallengeResponse initiate(AuthenticationContext context) {
            if (useRealInitiate) {
                return super.initiate(context);
            }
            return new OAuthChallengeResponse("challenge-id",
                    Instant.now().plusSeconds(600), authUrl + "?state=test");
        }
    }

    private static class StubOAuthConfig implements OAuthConfig {
        @Override public String clientId() { return "client-id"; }
        @Override public String clientSecret() { return "secret"; }
        @Override public String redirectUri() { return "https://example.com/callback"; }
    }

    private static class StubOAuthHttpClient implements OAuthHttpClient {
        @Override public OAuthTokenResponse exchangeCode(String e, Map<String, String> p) { return null; }
        @Override public Map<String, Object> fetchUserInfo(String e, String t) { return Map.of(); }
    }

    private static class StubOAuthTokenStore implements OAuthTokenStore {
        @Override public void store(OAuthTokenRecord r) {}
        @Override public Optional<OAuthTokenRecord> findByActorId(String a, String p, String t) { return Optional.empty(); }
        @Override public List<OAuthTokenRecord> findAllByActorId(String a, String t) { return List.of(); }
        @Override public void delete(String a, String p, String t) {}
        @Override public void updateTokens(String a, String p, String t, String at, String rt, Instant e) {}
    }

    private static class StubIdentityBindingStore implements IdentityBindingStore {
        @Override public void bind(IdentityBinding b) {}
        @Override public Optional<IdentityBinding> findByExternalId(String p, String e, String t) { return Optional.empty(); }
        @Override public Optional<IdentityBinding> findByActorId(String a, String p, String t) { return Optional.empty(); }
        @Override public void unbind(String p, String e, String t) {}
    }
}
