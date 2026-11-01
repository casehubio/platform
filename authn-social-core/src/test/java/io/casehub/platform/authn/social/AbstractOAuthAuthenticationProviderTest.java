package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.InvalidChallengeException;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UnknownIdentityException;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.authn.UserStatusResolver;
import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.registration.UserProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractOAuthAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private OAuthTestFixtures.InMemOAuthTokenStore tokenStore;
    private OAuthTestFixtures.InMemIdentityBindingStore bindingStore;
    private OAuthTestFixtures.StubUserResolver userResolver;
    private OAuthTestFixtures.StubOAuthHttpClient httpClient;
    private TestOAuthProvider provider;

    private StubUserProvisioner provisioner;
    private StubUserStatusResolver statusResolver;

    @BeforeEach
    void setUp() {
        tokenStore = new OAuthTestFixtures.InMemOAuthTokenStore();
        bindingStore = new OAuthTestFixtures.InMemIdentityBindingStore();
        userResolver = new OAuthTestFixtures.StubUserResolver();
        httpClient = new OAuthTestFixtures.StubOAuthHttpClient();
        provisioner = new StubUserProvisioner();
        statusResolver = new StubUserStatusResolver();

        var config = new TestOAuthConfig("client-id", "client-secret",
                "https://example.com/callback", Set.of("openid", "email"));
        provider = new TestOAuthProvider(config, httpClient, tokenStore, bindingStore,
                userResolver, provisioner, statusResolver);
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
        provisioner.nextResult = Optional.of(PrincipalId.human("ext-user-1"));
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
        provisioner.nextResult = Optional.of(PrincipalId.human("ext-user-1"));
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
        provisioner.nextResult = Optional.of(PrincipalId.human("ext-user-1"));
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

    @Test
    void verifyWithUserResolverMatch_doesNotCallProvisioner() {
        userResolver.resolvedPrincipal = PrincipalId.human("resolved-user");
        provisioner.nextResult = Optional.of(PrincipalId.human("should-not-be-used"));
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext("test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of("code", "auth-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("resolved-user"));
        assertThat(provisioner.called).isFalse();
    }

    @Test
    void verifyWithProvisionerSuccess_returnsProvisionedId() {
        provisioner.nextResult = Optional.of(PrincipalId.human("provisioned-user"));
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext("test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of("code", "auth-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("provisioned-user"));
        assertThat(provisioner.called).isTrue();
    }

    @Test
    void verifyWithBothEmpty_throwsUnknownIdentityException() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext("test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        assertThatThrownBy(() -> provider.verify(challenge, Map.of("code", "auth-code", "state", response.challengeId())))
                .isInstanceOf(UnknownIdentityException.class)
                .hasMessageContaining("No identity resolved");
    }

    @Test
    void verifyIncludesUserStatusInMetadata() {
        provisioner.nextResult = Optional.of(PrincipalId.human("prov-user"));
        statusResolver.nextStatus = Optional.of("PENDING_APPROVAL");
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext("test-provider", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(response.challengeId(), "test-provider", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of("code", "auth-code", "state", response.challengeId()));

        assertThat(result.metadata()).containsEntry("userStatus", "PENDING_APPROVAL");
    }

    // --- stubs ---

    private static class TestOAuthProvider extends AbstractOAuthAuthenticationProvider {
        TestOAuthProvider(OAuthConfig config, OAuthHttpClient httpClient,
                          OAuthTokenStore tokenStore, IdentityBindingStore bindingStore,
                          UserResolver userResolver, UserProvisioner provisioner,
                          UserStatusResolver statusResolver) {
            super(config, httpClient, tokenStore, bindingStore, userResolver,
                  OAuthTestFixtures.NO_OP_LISTENER, provisioner, statusResolver);
        }

        @Override
        public String method() { return "test-provider"; }

        @Override
        protected String authorizationEndpoint() { return "https://auth.test.com/authorize"; }

        @Override
        protected String tokenEndpoint() { return "https://auth.test.com/token"; }

        @Override
        protected OAuthIdentity extractIdentity(OAuthTokenResponse tokens) {
            return new OAuthIdentity("ext-user-1", "user@test.com", "Test User", true);
        }
    }

    private static class StubUserProvisioner implements UserProvisioner {
        Optional<PrincipalId> nextResult = Optional.empty();
        boolean called;

        @Override
        public Optional<PrincipalId> provisionFromSocialLogin(String email, String tenancyId,
                                                               String provider, boolean emailVerified) {
            called = true;
            return nextResult;
        }
    }

    private static class StubUserStatusResolver implements UserStatusResolver {
        Optional<String> nextStatus = Optional.empty();

        @Override
        public Optional<String> resolveStatus(String actorId, String tenancyId) {
            return nextStatus;
        }
    }

    private record TestOAuthConfig(String clientId, String clientSecret,
                                    String redirectUri, Set<String> scopes) implements OAuthConfig {}

}
