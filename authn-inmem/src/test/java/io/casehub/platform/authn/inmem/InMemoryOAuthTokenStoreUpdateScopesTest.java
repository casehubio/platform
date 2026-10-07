package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryOAuthTokenStoreUpdateScopesTest {

    private OAuthTokenStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryOAuthTokenStore();
    }

    @Test
    void updateScopesReplacesExistingScopes() {
        store.store(new OAuthTokenRecord(
                "actor1", "tenant1", "google",
                "access", "refresh",
                Set.of("openid", "email"),
                Instant.now().plusSeconds(3600),
                Instant.now()));

        store.updateScopes("actor1", "google", "tenant1",
                Set.of("openid", "email", "calendar.readonly"));

        var token = store.findByActorId("actor1", "google", "tenant1");
        assertTrue(token.isPresent());
        assertEquals(
                Set.of("openid", "email", "calendar.readonly"),
                token.get().grantedScopes());
    }

    @Test
    void updateScopesPreservesOtherFields() {
        var original = new OAuthTokenRecord(
                "actor1", "tenant1", "google",
                "my-access-token", "my-refresh-token",
                Set.of("openid"),
                Instant.now().plusSeconds(3600),
                Instant.now());
        store.store(original);

        store.updateScopes("actor1", "google", "tenant1",
                Set.of("openid", "calendar.readonly"));

        var updated = store.findByActorId("actor1", "google", "tenant1").get();
        assertEquals("my-access-token", updated.accessToken());
        assertEquals("my-refresh-token", updated.refreshToken());
    }

    @Test
    void updateScopesForNonExistentTokenIsNoOp() {
        assertDoesNotThrow(() ->
                store.updateScopes("actor1", "google", "tenant1",
                        Set.of("openid")));
    }
}
