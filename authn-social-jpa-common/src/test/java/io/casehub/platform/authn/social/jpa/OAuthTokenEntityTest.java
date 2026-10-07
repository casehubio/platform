package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTokenEntityTest {

    @Test
    void roundtrip() {
        var record = new OAuthTokenRecord(
                "alice", "tenant-1", "google",
                "access-tok", "refresh-tok",
                Set.of("email", "profile"),
                Instant.parse("2026-03-01T12:00:00Z"),
                Instant.parse("2026-01-15T10:30:00Z"));

        OAuthTokenEntity entity = OAuthTokenEntity.fromRecord(record);
        OAuthTokenRecord result = entity.toRecord();

        assertThat(result.actorId()).isEqualTo("alice");
        assertThat(result.tenancyId()).isEqualTo("tenant-1");
        assertThat(result.provider()).isEqualTo("google");
        assertThat(result.accessToken()).isEqualTo("access-tok");
        assertThat(result.refreshToken()).isEqualTo("refresh-tok");
        assertThat(result.grantedScopes()).containsExactlyInAnyOrder("email", "profile");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-03-01T12:00:00Z"));
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-01-15T10:30:00Z"));
    }

    @Test
    void roundtrip_emptyScopes() {
        var record = new OAuthTokenRecord(
                "bob", "tenant-2", "github",
                "access-tok", null, Set.of(), null,
                Instant.parse("2026-02-20T14:00:00Z"));

        OAuthTokenEntity entity = OAuthTokenEntity.fromRecord(record);
        OAuthTokenRecord result = entity.toRecord();

        assertThat(result.grantedScopes()).isEmpty();
        assertThat(result.refreshToken()).isNull();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    void roundtrip_nullCreatedAt_usesNow() {
        var record = new OAuthTokenRecord(
                "carol", "tenant-3", "apple",
                "access-tok", null, Set.of(), null, null);

        OAuthTokenEntity entity = OAuthTokenEntity.fromRecord(record);

        assertThat(entity.createdAt).isNotNull();
    }

    @Test
    void scopesToString_and_back() {
        assertThat(OAuthTokenEntity.scopesToString(Set.of("a", "b")))
                .contains("a").contains("b").contains(",");
        assertThat(OAuthTokenEntity.stringToScopes("a,b,c"))
                .containsExactlyInAnyOrder("a", "b", "c");
        assertThat(OAuthTokenEntity.scopesToString(Set.of())).isNull();
        assertThat(OAuthTokenEntity.scopesToString(null)).isNull();
        assertThat(OAuthTokenEntity.stringToScopes(null)).isEmpty();
        assertThat(OAuthTokenEntity.stringToScopes("")).isEmpty();
        assertThat(OAuthTokenEntity.stringToScopes("  ")).isEmpty();
    }
}
