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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private InMemOAuthTokenStore tokenStore;
    private InMemIdentityBindingStore bindingStore;
    private StubUserResolver userResolver;
    private StubOAuthHttpClient httpClient;
    private GoogleAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        tokenStore = new InMemOAuthTokenStore();
        bindingStore = new InMemIdentityBindingStore();
        userResolver = new StubUserResolver();
        httpClient = new StubOAuthHttpClient();

        var config = new GoogleAuthenticationProvider.GoogleConfig(
                "google-client-id", "google-client-secret",
                "https://example.com/callback");
        provider = new GoogleAuthenticationProvider(config, httpClient, tokenStore, bindingStore, userResolver, new io.casehub.platform.api.authn.AuthenticationEventListener() {});
    }

    @Test
    void methodReturnsGoogle() {
        assertThat(provider.method()).isEqualTo("google");
    }

    @Test
    void initiateUrlPointsToGoogleAuth() {
        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("accounts.google.com/o/oauth2/v2/auth");
        assertThat(response.authorizationUrl()).contains("client_id=google-client-id");
        assertThat(response.authorizationUrl()).contains("openid");
        assertThat(response.authorizationUrl()).contains("email");
        assertThat(response.authorizationUrl()).contains("profile");
    }

    @Test
    void verifyExtractsIdentityFromIdToken() {
        String idToken = buildIdToken("google-user-123", "user@gmail.com", "Jane Doe", true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", "refresh-token", idToken, 3600, "Bearer", "openid email profile");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "google-auth-code",
                "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("google-user-123"));
        assertThat(result.method()).isEqualTo("google");
        assertThat(result.metadata()).containsEntry("externalId", "google-user-123");
    }

    @Test
    void verifyRejectsUnverifiedEmail() {
        String idToken = buildIdToken("user-1", "user@gmail.com", "Jane", false);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, idToken, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        assertThatThrownBy(() -> provider.verify(challenge, Map.of(
                "code", "auth-code", "state", response.challengeId())))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("email not verified");
    }

    @Test
    void verifyFallsBackToUserInfoWhenNoIdToken() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid email");
        httpClient.nextUserInfo = Map.of(
                "sub", "google-user-456",
                "email", "user@gmail.com",
                "email_verified", true,
                "name", "John Doe");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "auth-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("google-user-456"));
    }

    @Test
    void verifyStoresTokenAndBinding() {
        String idToken = buildIdToken("google-user-789", "user@gmail.com", "Jane", true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", "refresh-token", idToken, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        provider.verify(challenge, Map.of("code", "auth-code", "state", response.challengeId()));

        assertThat(tokenStore.stored).hasSize(1);
        assertThat(bindingStore.stored).hasSize(1);
        var binding = bindingStore.stored.values().iterator().next();
        assertThat(binding.provider()).isEqualTo("google");
        assertThat(binding.externalId()).isEqualTo("google-user-789");
        assertThat(binding.email()).isEqualTo("user@gmail.com");
    }

    private static String buildIdToken(String sub, String email, String name, boolean emailVerified) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + sub + "\",\"email\":\"" + email
                        + "\",\"email_verified\":" + emailVerified
                        + ",\"name\":\"" + name + "\"}").getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("fake-signature".getBytes());
        return header + "." + payload + "." + signature;
    }

    // --- stubs (reused from AbstractOAuthAuthenticationProviderTest) ---

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
                                  String accessToken, String refreshToken, Instant expiresAt) {}
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
