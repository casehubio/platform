package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.ProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(result.provider()).isEqualTo("google");
        assertThat(result.requestedScopes()).isEqualTo(Set.of("calendar.readonly"));
        assertThat(result.authorizationUrl()).isNotNull();
        assertThat(result.state()).isNotNull();
    }

    @Test
    void requestForUnknownProviderThrows() {
        var handler = new IncrementalConsentHandlerCore(Map.of());

        assertThatThrownBy(() -> handler.requestAdditionalScopes(
                        "actor1", "unknown", "tenant1",
                        Set.of("some.scope")))
                .isInstanceOf(ProviderUnavailableException.class)
                .satisfies(ex -> assertThat(((ProviderUnavailableException) ex).method()).isEqualTo("unknown"));
    }

    @Test
    void requestPassesAdditionalScopesViaHints() {
        var capturingProvider = new CapturingProvider("github");

        var handler = new IncrementalConsentHandlerCore(
                Map.of("github", capturingProvider));

        handler.requestAdditionalScopes(
                "actor1", "github", "tenant1",
                Set.of("repo", "user:email"));

        assertThat(capturingProvider.lastContext).isNotNull();
        var hints = capturingProvider.lastContext.hints();
        assertThat(hints).containsKey("additionalScopes");
        @SuppressWarnings("unchecked")
        var scopes = (Set<String>) hints.get("additionalScopes");
        assertThat(scopes).contains("repo", "user:email");
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
            super(new StubOAuthConfig(), new OAuthTestFixtures.StubOAuthHttpClient(),
                    new OAuthTestFixtures.InMemOAuthTokenStore(), new OAuthTestFixtures.InMemIdentityBindingStore(),
                    (email, tenancyId) -> Optional.empty(),
                    OAuthTestFixtures.NO_OP_LISTENER);
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

}
