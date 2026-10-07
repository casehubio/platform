package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.ConsentRequest;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.IncrementalConsentHandler;
import io.casehub.platform.api.authn.InsufficientScopesException;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.RequiresScopes;
import io.casehub.platform.api.authn.ScopeRegistry;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.authn.inmem.InMemoryOAuthTokenStore;
import io.casehub.platform.authn.inmem.InMemoryStaticCredentialStore;
import io.casehub.platform.authn.social.AbstractOAuthAuthenticationProvider;
import io.casehub.platform.authn.social.IncrementalConsentHandlerCore;
import io.casehub.platform.authn.social.OAuthChallengeResponse;
import io.casehub.platform.authn.social.OAuthConfig;
import io.casehub.platform.authn.social.OAuthHttpClient;
import io.casehub.platform.authn.social.OAuthIdentity;
import io.casehub.platform.authn.social.OAuthTokenManagerCore;
import io.casehub.platform.authn.social.OAuthTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceConnectionE2ETest {

    @Nested
    class StaticCredentialLifecycle {
        private final StaticCredentialStore store = new InMemoryStaticCredentialStore();

        @Test
        void fullCrudLifecycle() {
            var apiKey = new StaticCredentialRecord(
                    "user-1", "tenant-1", "google-maps",
                    "AIza-real-key", CredentialType.API_KEY,
                    Instant.now(), Instant.now());

            store.store(apiKey);
            assertThat(store.find("user-1", "google-maps", "tenant-1"))
                    .isPresent()
                    .get().satisfies(r -> {
                        assertThat(r.credential()).isEqualTo("AIza-real-key");
                        assertThat(r.type()).isEqualTo(CredentialType.API_KEY);
                        assertThat(r.provider()).isEqualTo("google-maps");
                    });

            var pat = new StaticCredentialRecord(
                    "user-1", "tenant-1", "github",
                    "ghp_abc123", CredentialType.PERSONAL_ACCESS_TOKEN,
                    Instant.now(), Instant.now());
            store.store(pat);
            assertThat(store.findAll("user-1", "tenant-1")).hasSize(2);

            var updated = new StaticCredentialRecord(
                    "user-1", "tenant-1", "google-maps",
                    "AIza-rotated-key", CredentialType.API_KEY,
                    apiKey.createdAt(), Instant.now());
            store.store(updated);
            assertThat(store.find("user-1", "google-maps", "tenant-1").get().credential())
                    .isEqualTo("AIza-rotated-key");

            store.delete("user-1", "google-maps", "tenant-1");
            assertThat(store.find("user-1", "google-maps", "tenant-1")).isEmpty();
            assertThat(store.findAll("user-1", "tenant-1")).hasSize(1);
        }

        @Test
        void tenantIsolation() {
            store.store(new StaticCredentialRecord(
                    "user-1", "tenant-a", "slack",
                    "token-a", CredentialType.BOT_TOKEN,
                    Instant.now(), Instant.now()));
            store.store(new StaticCredentialRecord(
                    "user-1", "tenant-b", "slack",
                    "token-b", CredentialType.BOT_TOKEN,
                    Instant.now(), Instant.now()));

            assertThat(store.findAll("user-1", "tenant-a")).hasSize(1);
            assertThat(store.find("user-1", "slack", "tenant-a").get().credential())
                    .isEqualTo("token-a");
            assertThat(store.find("user-1", "slack", "tenant-b").get().credential())
                    .isEqualTo("token-b");
        }
    }

    @Nested
    class ScopeValidationFlow {
        private final ScopeRegistry scopeRegistry = new ScopeRegistryCore();
        private final OAuthTokenStore tokenStore = new InMemoryOAuthTokenStore();

        @BeforeEach
        void setUp() {
            scopeRegistry.register("google",
                    Set.of("https://www.googleapis.com/auth/calendar.readonly",
                            "https://www.googleapis.com/auth/calendar.events"),
                    FakeCalendarConsumer.class);

            scopeRegistry.register("google",
                    Set.of("https://www.googleapis.com/auth/contacts.readonly"),
                    FakeContactsConsumer.class);
        }

        @Test
        void detectMissingScopesAfterSocialLogin() {
            tokenStore.store(new OAuthTokenRecord(
                    "user-1", "tenant-1", "google",
                    "access-token", "refresh-token",
                    Set.of("openid", "email", "profile"),
                    Instant.now().plusSeconds(3600), Instant.now()));

            var token = tokenStore.findByActorId("user-1", "google", "tenant-1").orElseThrow();
            var missing = scopeRegistry.missingScopes("google", token.grantedScopes());

            assertThat(missing).containsExactlyInAnyOrder(
                    "https://www.googleapis.com/auth/calendar.readonly",
                    "https://www.googleapis.com/auth/calendar.events",
                    "https://www.googleapis.com/auth/contacts.readonly");
            assertThat(scopeRegistry.satisfies("google", token.grantedScopes())).isFalse();
        }

        @Test
        void throwInsufficientScopesExceptionWithStructuredMetadata() {
            var granted = Set.of("openid", "email");
            var required = scopeRegistry.requiredScopes("google");
            var missing = scopeRegistry.missingScopes("google", granted);

            assertThatThrownBy(() -> {
                if (!missing.isEmpty()) {
                    throw new InsufficientScopesException("google", "user-1", required, granted, missing);
                }
            })
            .isInstanceOf(InsufficientScopesException.class)
            .satisfies(ex -> {
                var ise = (InsufficientScopesException) ex;
                assertThat(ise.getProvider()).isEqualTo("google");
                assertThat(ise.getActorId()).isEqualTo("user-1");
                assertThat(ise.getMissingScopes()).isNotEmpty();
                assertThat(ise.getGrantedScopes()).containsExactlyInAnyOrder("openid", "email");
                assertThat(ise.getMessage()).contains("missing scopes");
            });
        }

        @Test
        void scopesSatisfiedAfterIncrementalConsent() {
            tokenStore.store(new OAuthTokenRecord(
                    "user-1", "tenant-1", "google",
                    "access-token", "refresh-token",
                    Set.of("openid", "email", "profile"),
                    Instant.now().plusSeconds(3600), Instant.now()));

            assertThat(scopeRegistry.satisfies("google",
                    tokenStore.findByActorId("user-1", "google", "tenant-1")
                            .orElseThrow().grantedScopes())).isFalse();

            var allScopes = Set.of("openid", "email", "profile",
                    "https://www.googleapis.com/auth/calendar.readonly",
                    "https://www.googleapis.com/auth/calendar.events",
                    "https://www.googleapis.com/auth/contacts.readonly");
            tokenStore.updateScopes("user-1", "google", "tenant-1", allScopes);

            assertThat(scopeRegistry.satisfies("google",
                    tokenStore.findByActorId("user-1", "google", "tenant-1")
                            .orElseThrow().grantedScopes())).isTrue();
        }

        @RequiresScopes(provider = "google",
                scopes = {"https://www.googleapis.com/auth/calendar.readonly",
                        "https://www.googleapis.com/auth/calendar.events"})
        static class FakeCalendarConsumer {}

        @RequiresScopes(provider = "google",
                scopes = {"https://www.googleapis.com/auth/contacts.readonly"})
        static class FakeContactsConsumer {}
    }

    @Nested
    class IncrementalConsentFlow {
        private final OAuthTokenStore tokenStore = new InMemoryOAuthTokenStore();
        private final ScopeRegistry scopeRegistry = new ScopeRegistryCore();

        @Test
        void endToEndConsentFlow() {
            scopeRegistry.register("google",
                    Set.of("calendar.readonly", "calendar.events"),
                    Object.class);

            tokenStore.store(new OAuthTokenRecord(
                    "user-1", "tenant-1", "google",
                    "access", "refresh",
                    Set.of("openid", "email"),
                    Instant.now().plusSeconds(3600), Instant.now()));

            var token = tokenStore.findByActorId("user-1", "google", "tenant-1").orElseThrow();
            var missing = scopeRegistry.missingScopes("google", token.grantedScopes());
            assertThat(missing).containsExactlyInAnyOrder("calendar.readonly", "calendar.events");

            var stubProvider = new StubOAuthProvider("google");
            IncrementalConsentHandler handler = new IncrementalConsentHandlerCore(
                    Map.of("google", stubProvider));

            ConsentRequest consent = handler.requestAdditionalScopes(
                    "user-1", "google", "tenant-1", missing);

            assertThat(consent.provider()).isEqualTo("google");
            assertThat(consent.requestedScopes()).containsExactlyInAnyOrder(
                    "calendar.readonly", "calendar.events");
            assertThat(consent.authorizationUrl()).contains("google");
            assertThat(consent.state()).isNotBlank();

            var mergedScopes = new java.util.HashSet<>(token.grantedScopes());
            mergedScopes.addAll(Set.of("calendar.readonly", "calendar.events"));
            tokenStore.updateScopes("user-1", "google", "tenant-1", mergedScopes);

            assertThat(scopeRegistry.satisfies("google",
                    tokenStore.findByActorId("user-1", "google", "tenant-1")
                            .orElseThrow().grantedScopes())).isTrue();
        }
    }

    @Nested
    class ScopeRegistryAnnotationDiscovery {

        @Test
        void manualScanReflectsAnnotations() {
            var registry = new ScopeRegistryCore();

            for (var clazz : List.of(
                    AnnotatedCalendar.class,
                    AnnotatedDrive.class)) {
                var ann = clazz.getAnnotation(RequiresScopes.class);
                if (ann != null) {
                    registry.register(ann.provider(), Set.of(ann.scopes()), clazz);
                }
            }

            assertThat(registry.requiredScopes("google"))
                    .containsExactlyInAnyOrder("calendar.readonly", "drive.readonly");
            assertThat(registry.requiredScopes("google", AnnotatedCalendar.class))
                    .containsExactly("calendar.readonly");
            assertThat(registry.satisfies("google",
                    Set.of("calendar.readonly", "drive.readonly", "email"))).isTrue();
            assertThat(registry.satisfies("google",
                    Set.of("calendar.readonly"))).isFalse();
        }

        @RequiresScopes(provider = "google", scopes = "calendar.readonly")
        static class AnnotatedCalendar {}

        @RequiresScopes(provider = "google", scopes = "drive.readonly")
        static class AnnotatedDrive {}
    }

    private static class StubOAuthProvider extends AbstractOAuthAuthenticationProvider {
        StubOAuthProvider(String method) {
            super(new StubConfig(), new StubHttpClient(),
                    new InMemoryOAuthTokenStore(),
                    new StubBindingStore(),
                    (email, tenancyId) -> Optional.empty(),
                    new io.casehub.platform.api.authn.AuthenticationEventListener() {});
        }

        @Override public String method() { return "google"; }
        @Override protected String authorizationEndpoint() { return "https://accounts.google.com/o/oauth2/auth"; }
        @Override protected String tokenEndpoint() { return "https://accounts.google.com/token"; }
        @Override protected OAuthIdentity extractIdentity(OAuthTokenResponse r) { return null; }

        @Override
        public ChallengeResponse initiate(AuthenticationContext context) {
            return new OAuthChallengeResponse(
                    java.util.UUID.randomUUID().toString(),
                    Instant.now().plusSeconds(600),
                    "https://accounts.google.com/o/oauth2/auth?scope=calendar&state=test");
        }
    }

    private static class StubConfig implements OAuthConfig {
        @Override public String clientId() { return "client-id"; }
        @Override public String clientSecret() { return "secret"; }
        @Override public String redirectUri() { return "https://example.com/callback"; }
    }

    private static class StubHttpClient implements OAuthHttpClient {
        @Override public OAuthTokenResponse exchangeCode(String e, Map<String, String> p) { return null; }
        @Override public Map<String, Object> fetchUserInfo(String e, String t) { return Map.of(); }
    }

    private static class StubBindingStore implements IdentityBindingStore {
        @Override public void bind(IdentityBinding b) {}
        @Override public Optional<IdentityBinding> findByExternalId(String p, String e, String t) { return Optional.empty(); }
        @Override public Optional<IdentityBinding> findByActorId(String a, String p, String t) { return Optional.empty(); }
        @Override public void unbind(String p, String e, String t) {}
    }
}
