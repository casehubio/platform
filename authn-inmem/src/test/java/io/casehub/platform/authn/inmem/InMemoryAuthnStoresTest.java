package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.RefreshTokenRecord;
import io.casehub.platform.api.authn.SessionRecord;
import io.casehub.platform.api.authn.WebAuthnCredential;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuthnStoresTest {

    @Nested
    class ChallengeStoreTest {
        private final InMemoryChallengeStore store = new InMemoryChallengeStore();

        @Test
        void storeAndConsume() {
            var record = new ChallengeRecord("c-1", "webauthn", "t-1", null, Instant.now(), Instant.now().plusSeconds(300));
            store.store(record);
            assertThat(store.consume("c-1")).isPresent();
            assertThat(store.consume("c-1")).isEmpty();
        }

        @Test
        void consumeNonexistentReturnsEmpty() {
            assertThat(store.consume("missing")).isEmpty();
        }
    }

    @Nested
    class SessionStoreTest {
        private final InMemorySessionStore store = new InMemorySessionStore();

        @Test
        void storeAndFind() {
            var session = session("s-1", "actor-1", "t-1");
            store.store(session);
            assertThat(store.findById("s-1")).isPresent();
        }

        @Test
        void deleteRemovesSession() {
            store.store(session("s-1", "actor-1", "t-1"));
            store.delete("s-1");
            assertThat(store.findById("s-1")).isEmpty();
        }

        @Test
        void deleteByActorIdRemovesMatching() {
            store.store(session("s-1", "actor-1", "t-1"));
            store.store(session("s-2", "actor-1", "t-1"));
            store.store(session("s-3", "actor-2", "t-1"));
            store.deleteByActorId("actor-1", "t-1");
            assertThat(store.size()).isEqualTo(1);
            assertThat(store.findById("s-3")).isPresent();
        }

        private SessionRecord session(String id, String actorId, String tenancyId) {
            return new SessionRecord(id, actorId, tenancyId, Set.of(), "webauthn", "csrf", Instant.now(), Instant.now().plusSeconds(3600), null);
        }
    }

    @Nested
    class RefreshTokenStoreTest {
        private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

        @Test
        void storeAndFind() {
            store.store(refreshToken("t-1", "f-1", false));
            assertThat(store.findByToken("t-1")).isPresent();
        }

        @Test
        void consumeMarksAsConsumed() {
            store.store(refreshToken("t-1", "f-1", false));
            store.consume("t-1");
            assertThat(store.findByToken("t-1").get().consumed()).isTrue();
        }

        @Test
        void revokeFamilyRemovesAll() {
            store.store(refreshToken("t-1", "f-1", false));
            store.store(refreshToken("t-2", "f-1", true));
            store.store(refreshToken("t-3", "f-2", false));
            store.revokeFamily("f-1");
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        void purgeExpiredRemovesOld() {
            store.store(new RefreshTokenRecord("t-1", "f-1", "actor", "tenant", Set.of(), "webauthn",
                    Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), false));
            store.store(refreshToken("t-2", "f-2", false));
            int purged = store.purgeExpired(Instant.now());
            assertThat(purged).isEqualTo(1);
            assertThat(store.size()).isEqualTo(1);
        }

        private RefreshTokenRecord refreshToken(String token, String familyId, boolean consumed) {
            return new RefreshTokenRecord(token, familyId, "actor", "tenant", Set.of(), "webauthn",
                    Instant.now(), Instant.now().plusSeconds(86400), consumed);
        }
    }

    @Nested
    class WebAuthnCredentialStoreTest {
        private final InMemoryWebAuthnCredentialStore store = new InMemoryWebAuthnCredentialStore();

        @Test
        void storeAndFind() {
            store.store(credential("cred-1", "actor-1", "t-1"));
            assertThat(store.findByCredentialId("cred-1")).isPresent();
        }

        @Test
        void findByActorIdReturnsMatching() {
            store.store(credential("cred-1", "actor-1", "t-1"));
            store.store(credential("cred-2", "actor-1", "t-1"));
            store.store(credential("cred-3", "actor-2", "t-1"));
            assertThat(store.findByActorId("actor-1", "t-1")).hasSize(2);
        }

        @Test
        void updateAfterAuthentication() {
            store.store(credential("cred-1", "actor-1", "t-1"));
            var now = Instant.now();
            store.updateAfterAuthentication("cred-1", 42, now);
            var updated = store.findByCredentialId("cred-1").orElseThrow();
            assertThat(updated.signCount()).isEqualTo(42);
            assertThat(updated.lastUsedAt()).isEqualTo(now);
        }

        @Test
        void deleteRemoves() {
            store.store(credential("cred-1", "actor-1", "t-1"));
            store.delete("cred-1");
            assertThat(store.findByCredentialId("cred-1")).isEmpty();
        }

        private WebAuthnCredential credential(String credId, String actorId, String tenancyId) {
            return new WebAuthnCredential(credId, actorId, tenancyId, new byte[]{1, 2, 3}, 0, Set.of("usb"), null, "Key", Instant.now(), null, true);
        }
    }

    @Nested
    class OAuthTokenStoreTest {
        private final InMemoryOAuthTokenStore store = new InMemoryOAuthTokenStore();

        @Test
        void storeAndFind() {
            store.store(oauthToken("actor-1", "google", "t-1"));
            assertThat(store.findByActorId("actor-1", "google", "t-1")).isPresent();
        }

        @Test
        void findAllByActorIdReturnsAllProviders() {
            store.store(oauthToken("actor-1", "google", "t-1"));
            store.store(oauthToken("actor-1", "github", "t-1"));
            assertThat(store.findAllByActorId("actor-1", "t-1")).hasSize(2);
        }

        @Test
        void deleteRemoves() {
            store.store(oauthToken("actor-1", "google", "t-1"));
            store.delete("actor-1", "google", "t-1");
            assertThat(store.findByActorId("actor-1", "google", "t-1")).isEmpty();
        }

        @Test
        void updateTokensReplacesCredentials() {
            store.store(oauthToken("actor-1", "google", "t-1"));
            var newExpiry = Instant.now().plusSeconds(7200);
            store.updateTokens("actor-1", "google", "t-1", "new-access", "new-refresh", newExpiry);
            var updated = store.findByActorId("actor-1", "google", "t-1").orElseThrow();
            assertThat(updated.accessToken()).isEqualTo("new-access");
            assertThat(updated.refreshToken()).isEqualTo("new-refresh");
            assertThat(updated.expiresAt()).isEqualTo(newExpiry);
        }

        private OAuthTokenRecord oauthToken(String actorId, String provider, String tenancyId) {
            return new OAuthTokenRecord(actorId, tenancyId, provider, "access-token", "refresh-token",
                    Set.of("openid"), Instant.now().plusSeconds(3600), Instant.now());
        }
    }

    @Nested
    class IdentityBindingStoreTest {
        private final InMemoryIdentityBindingStore store = new InMemoryIdentityBindingStore();

        @Test
        void bindAndFindByExternalId() {
            store.bind(binding("google", "ext-1", "actor-1", "t-1"));
            assertThat(store.findByExternalId("google", "ext-1", "t-1")).isPresent();
        }

        @Test
        void findByActorId() {
            store.bind(binding("google", "ext-1", "actor-1", "t-1"));
            assertThat(store.findByActorId("actor-1", "google", "t-1")).isPresent();
        }

        @Test
        void unbindRemovesBothIndexes() {
            store.bind(binding("google", "ext-1", "actor-1", "t-1"));
            store.unbind("google", "ext-1", "t-1");
            assertThat(store.findByExternalId("google", "ext-1", "t-1")).isEmpty();
            assertThat(store.findByActorId("actor-1", "google", "t-1")).isEmpty();
        }

        private IdentityBinding binding(String provider, String externalId, String actorId, String tenancyId) {
            return new IdentityBinding(provider, externalId, actorId, tenancyId, "user@example.com", Instant.now());
        }
    }
}
