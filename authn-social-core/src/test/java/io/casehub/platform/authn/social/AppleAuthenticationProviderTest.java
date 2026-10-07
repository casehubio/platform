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

class AppleAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private InMemOAuthTokenStore tokenStore;
    private InMemIdentityBindingStore bindingStore;
    private StubOAuthHttpClient httpClient;
    private AppleAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        tokenStore = new InMemOAuthTokenStore();
        bindingStore = new InMemIdentityBindingStore();
        httpClient = new StubOAuthHttpClient();
        var userResolver = new StubUserResolver();

        var config = new AppleAuthenticationProvider.AppleConfig(
                "com.example.service",
                "team-id-123",
                "key-id-abc",
                "fake-private-key",
                "https://example.com/callback");
        provider = new AppleAuthenticationProvider(config, httpClient, tokenStore, bindingStore, userResolver, new io.casehub.platform.api.authn.AuthenticationEventListener() {});
    }

    @Test
    void methodReturnsApple() {
        assertThat(provider.method()).isEqualTo("apple");
    }

    @Test
    void initiateUrlPointsToApple() {
        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("appleid.apple.com/auth/authorize");
        assertThat(response.authorizationUrl()).contains("response_mode=form_post");
    }

    @Test
    void verifyExtractsIdentityFromIdToken() {
        String idToken = buildIdToken("apple-user-001", "user@privaterelay.appleid.com", true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "apple-access-token", "apple-refresh-token", idToken, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "apple", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "apple-auth-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("apple-user-001"));
        assertThat(result.method()).isEqualTo("apple");
    }

    @Test
    void verifyUsesFirstLoginUserData() {
        String idToken = buildIdToken("apple-user-002", null, true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "token", null, idToken, 3600, "Bearer", "openid");

        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "apple", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "code",
                "state", response.challengeId(),
                "user", "{\"name\":{\"firstName\":\"Jane\",\"lastName\":\"Doe\"},\"email\":\"jane@example.com\"}"));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("apple-user-002"));
    }

    @Test
    void verifyRejectsNoIdToken() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "apple", TENANT,
                null, Instant.now(), response.expiresAt());

        assertThatThrownBy(() -> provider.verify(challenge, Map.of(
                "code", "code", "state", response.challengeId())))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("ID token");
    }

    private static String buildIdToken(String sub, String email, boolean emailVerified) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        StringBuilder payloadJson = new StringBuilder("{\"sub\":\"").append(sub).append("\"");
        if (email != null) {
            payloadJson.append(",\"email\":\"").append(email).append("\"");
        }
        payloadJson.append(",\"email_verified\":").append(emailVerified).append("}");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.toString().getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("sig".getBytes());
        return header + "." + payload + "." + signature;
    }

    // --- stubs ---

    private static class StubOAuthHttpClient implements OAuthHttpClient {
        OAuthTokenResponse nextTokenResponse;
        @Override public OAuthTokenResponse exchangeCode(String e, Map<String, String> p) { return nextTokenResponse; }
        @Override public Map<String, Object> fetchUserInfo(String e, String a) { return Map.of(); }
    }

    private static class StubUserResolver implements UserResolver {
        @Override public Optional<PrincipalId> resolveByEmail(String e, String t) { return Optional.empty(); }
    }

    private static class InMemOAuthTokenStore implements OAuthTokenStore {
        final ConcurrentHashMap<String, OAuthTokenRecord> stored = new ConcurrentHashMap<>();
        @Override public void store(OAuthTokenRecord r) { stored.put(r.actorId(), r); }
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
