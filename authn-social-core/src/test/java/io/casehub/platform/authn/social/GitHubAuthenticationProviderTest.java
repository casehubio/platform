package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private InMemOAuthTokenStore tokenStore;
    private InMemIdentityBindingStore bindingStore;
    private StubOAuthHttpClient httpClient;
    private GitHubAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        tokenStore = new InMemOAuthTokenStore();
        bindingStore = new InMemIdentityBindingStore();
        httpClient = new StubOAuthHttpClient();
        var userResolver = new StubUserResolver();

        var config = new GitHubAuthenticationProvider.GitHubConfig(
                "github-client-id", "github-client-secret",
                "https://example.com/callback");
        provider = new GitHubAuthenticationProvider(config, httpClient, tokenStore, bindingStore, userResolver);
    }

    @Test
    void methodReturnsGithub() {
        assertThat(provider.method()).isEqualTo("github");
    }

    @Test
    void initiateUrlPointsToGitHub() {
        var context = new AuthenticationContext(
                "github", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("github.com/login/oauth/authorize");
        assertThat(response.authorizationUrl()).contains("client_id=github-client-id");
    }

    @Test
    void verifyExtractsIdentityFromUserApi() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "gho_access_token", null, null, 0, "bearer", "user:email");
        httpClient.nextUserInfo = Map.of(
                "id", 12345,
                "login", "octocat",
                "name", "The Octocat",
                "email", "octocat@github.com");

        var context = new AuthenticationContext(
                "github", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "github", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "github-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("12345"));
        assertThat(result.method()).isEqualTo("github");
    }

    @Test
    void verifyHandlesNumericId() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "gho_token", null, null, 0, "bearer", "user");
        httpClient.nextUserInfo = Map.of(
                "id", 99999,
                "login", "devuser");

        var context = new AuthenticationContext(
                "github", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "github", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "code", "state", response.challengeId()));

        assertThat(result.principal().id()).isEqualTo("99999");
        assertThat(bindingStore.stored).hasSize(1);
        assertThat(bindingStore.stored.values().iterator().next().externalId()).isEqualTo("99999");
    }

    // --- stubs ---

    private static class StubOAuthHttpClient implements OAuthHttpClient {
        OAuthTokenResponse nextTokenResponse;
        Map<String, Object> nextUserInfo;

        @Override
        public OAuthTokenResponse exchangeCode(String tokenEndpoint, Map<String, String> params) {
            return nextTokenResponse;
        }

        @Override
        public Map<String, Object> fetchUserInfo(String userInfoEndpoint, String accessToken) {
            return nextUserInfo != null ? nextUserInfo : Map.of();
        }
    }

    private static class StubUserResolver implements UserResolver {
        @Override
        public Optional<PrincipalId> resolveByEmail(String email, String tenancyId) {
            return Optional.empty();
        }
    }

    private static class InMemOAuthTokenStore implements OAuthTokenStore {
        final ConcurrentHashMap<String, OAuthTokenRecord> stored = new ConcurrentHashMap<>();
        @Override public void store(OAuthTokenRecord record) {
            stored.put(record.actorId() + ":" + record.provider(), record);
        }
        @Override public Optional<OAuthTokenRecord> findByActorId(String a, String p, String t) { return Optional.empty(); }
        @Override public List<OAuthTokenRecord> findAllByActorId(String a, String t) { return List.of(); }
        @Override public void delete(String a, String p, String t) {}
        @Override public void updateTokens(String a, String p, String t, String at, String rt, Instant e) {}
    }

    private static class InMemIdentityBindingStore implements IdentityBindingStore {
        final ConcurrentHashMap<String, IdentityBinding> stored = new ConcurrentHashMap<>();
        @Override public void bind(IdentityBinding b) {
            stored.put(b.provider() + ":" + b.externalId() + ":" + b.tenancyId(), b);
        }
        @Override public Optional<IdentityBinding> findByExternalId(String p, String e, String t) {
            return Optional.ofNullable(stored.get(p + ":" + e + ":" + t));
        }
        @Override public Optional<IdentityBinding> findByActorId(String a, String p, String t) { return Optional.empty(); }
        @Override public void unbind(String p, String e, String t) {}
    }
}
