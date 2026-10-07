package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.InvalidChallengeException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractOAuthAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private InMemOAuthTokenStore tokenStore;
    private InMemIdentityBindingStore bindingStore;
    private StubUserResolver userResolver;
    private StubOAuthHttpClient httpClient;
    private TestOAuthProvider provider;

    @BeforeEach
    void setUp() {
        tokenStore = new InMemOAuthTokenStore();
        bindingStore = new InMemIdentityBindingStore();
        userResolver = new StubUserResolver();
        httpClient = new StubOAuthHttpClient();

        var config = new TestOAuthConfig("client-id", "client-secret",
                "https://example.com/callback", Set.of("openid", "email"));
        provider = new TestOAuthProvider(config, httpClient, tokenStore, bindingStore, userResolver);
    }

    @Test
    void methodReturnsProviderName() {
        assertThat(provider.method()).isEqualTo("test-provider");
    }

    @Test
    void initiateReturnsAuthorizationUrl() {
        var context = new AuthenticationContext(
                "test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("https://auth.test.com/authorize");
        assertThat(response.authorizationUrl()).contains("client_id=client-id");
        assertThat(response.authorizationUrl()).contains("redirect_uri=");
        assertThat(response.authorizationUrl()).contains("scope=");
        assertThat(response.authorizationUrl()).contains("openid");
        assertThat(response.authorizationUrl()).contains("email");
        assertThat(response.authorizationUrl()).contains("state=");
        assertThat(response.authorizationUrl()).contains("response_type=code");
    }

    @Test
    void initiateIncludesAdditionalScopes() {
        var context = new AuthenticationContext(
                "test-provider", TENANT, ORIGIN, Optional.empty(),
                Map.of("additionalScopes", "profile"));
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("scope=");
    }

    @Test
    void verifyExchangesCodeAndReturnsResult() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", "refresh-token", null, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        var challenge = new ChallengeRecord(
                response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "auth-code-123",
                "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("ext-user-1"));
        assertThat(result.tenancyId()).isEqualTo(TENANT);
        assertThat(result.method()).isEqualTo("test-provider");
    }

    @Test
    void verifyStoresTokens() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", "refresh-token", null, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        var challenge = new ChallengeRecord(
                response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());
        provider.verify(challenge, Map.of("code", "auth-code-123", "state", response.challengeId()));

        assertThat(tokenStore.stored).hasSize(1);
        var token = tokenStore.stored.values().iterator().next();
        assertThat(token.accessToken()).isEqualTo("access-token");
        assertThat(token.refreshToken()).isEqualTo("refresh-token");
        assertThat(token.provider()).isEqualTo("test-provider");
    }

    @Test
    void verifyCreatesIdentityBinding() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext(
                "test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        provider.verify(challenge, Map.of("code", "auth-code", "state", response.challengeId()));

        assertThat(bindingStore.stored).hasSize(1);
        var binding = bindingStore.stored.values().iterator().next();
        assertThat(binding.provider()).isEqualTo("test-provider");
        assertThat(binding.externalId()).isEqualTo("ext-user-1");
    }

    @Test
    void verifyReusesExistingBinding() {
        bindingStore.bind(new IdentityBinding(
                "test-provider", "ext-user-1", "existing-actor", TENANT,
                "user@test.com", Instant.now()));

        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext(
                "test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "auth-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("existing-actor"));
        assertThat(bindingStore.stored).hasSize(1);
    }

    @Test
    void verifyRejectsMissingCode() {
        var context = new AuthenticationContext(
                "test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        assertThatThrownBy(() -> provider.verify(challenge, Map.of("state", response.challengeId())))
                .isInstanceOf(InvalidChallengeException.class)
                .hasMessageContaining("code");
    }

    // --- stubs ---

    private static class TestOAuthProvider extends AbstractOAuthAuthenticationProvider {
        TestOAuthProvider(OAuthConfig config, OAuthHttpClient httpClient,
                          OAuthTokenStore tokenStore, IdentityBindingStore bindingStore,
                          UserResolver userResolver) {
            super(config, httpClient, tokenStore, bindingStore, userResolver,
                  new io.casehub.platform.api.authn.AuthenticationEventListener() {});
        }

        @Override
        public String method() { return "test-provider"; }

        @Override
        protected String authorizationEndpoint() { return "https://auth.test.com/authorize"; }

        @Override
        protected String tokenEndpoint() { return "https://auth.test.com/token"; }

        @Override
        protected OAuthIdentity extractIdentity(OAuthTokenResponse tokens) {
            return new OAuthIdentity("ext-user-1", "user@test.com", "Test User");
        }
    }

    private record TestOAuthConfig(String clientId, String clientSecret,
                                    String redirectUri, Set<String> scopes) implements OAuthConfig {}

    private static class StubOAuthHttpClient implements OAuthHttpClient {
        OAuthTokenResponse nextTokenResponse;

        @Override
        public OAuthTokenResponse exchangeCode(String tokenEndpoint, Map<String, String> params) {
            return nextTokenResponse;
        }

        @Override
        public Map<String, Object> fetchUserInfo(String userInfoEndpoint, String accessToken) {
            return Map.of();
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

        @Override
        public void store(OAuthTokenRecord record) {
            stored.put(record.actorId() + ":" + record.provider() + ":" + record.tenancyId(), record);
        }

        @Override
        public Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId) {
            return Optional.ofNullable(stored.get(actorId + ":" + provider + ":" + tenancyId));
        }

        @Override
        public List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId) {
            return stored.values().stream()
                    .filter(r -> r.actorId().equals(actorId) && r.tenancyId().equals(tenancyId))
                    .toList();
        }

        @Override
        public void delete(String actorId, String provider, String tenancyId) {
            stored.remove(actorId + ":" + provider + ":" + tenancyId);
        }

        @Override
        public void updateTokens(String actorId, String provider, String tenancyId,
                                  String accessToken, String refreshToken, Instant expiresAt) {
            stored.computeIfPresent(actorId + ":" + provider + ":" + tenancyId,
                    (k, old) -> new OAuthTokenRecord(actorId, tenancyId, provider,
                            accessToken, refreshToken, old.grantedScopes(), expiresAt, old.createdAt()));
        }
    }

    private static class InMemIdentityBindingStore implements IdentityBindingStore {
        final ConcurrentHashMap<String, IdentityBinding> stored = new ConcurrentHashMap<>();

        @Override
        public void bind(IdentityBinding binding) {
            stored.put(binding.provider() + ":" + binding.externalId() + ":" + binding.tenancyId(), binding);
        }

        @Override
        public Optional<IdentityBinding> findByExternalId(String provider, String externalId, String tenancyId) {
            return Optional.ofNullable(stored.get(provider + ":" + externalId + ":" + tenancyId));
        }

        @Override
        public Optional<IdentityBinding> findByActorId(String actorId, String provider, String tenancyId) {
            return stored.values().stream()
                    .filter(b -> b.actorId().equals(actorId) && b.provider().equals(provider)
                            && b.tenancyId().equals(tenancyId))
                    .findFirst();
        }

        @Override
        public void unbind(String provider, String externalId, String tenancyId) {
            stored.remove(provider + ":" + externalId + ":" + tenancyId);
        }
    }
}
