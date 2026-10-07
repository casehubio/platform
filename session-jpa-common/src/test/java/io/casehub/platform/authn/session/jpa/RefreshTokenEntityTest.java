package io.casehub.platform.authn.session.jpa;

import io.casehub.platform.api.authn.RefreshTokenRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenEntityTest {

    @Test
    void roundtrip_allFields() {
        var record = new RefreshTokenRecord(
                "tok-abc", "fam-1", "alice", "tenant-1",
                Set.of("admin"), "webauthn",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                false);

        RefreshTokenEntity entity = RefreshTokenEntity.fromRecord(record);
        RefreshTokenRecord result = entity.toRecord();

        assertThat(result.token()).isEqualTo("tok-abc");
        assertThat(result.familyId()).isEqualTo("fam-1");
        assertThat(result.actorId()).isEqualTo("alice");
        assertThat(result.tenancyId()).isEqualTo("tenant-1");
        assertThat(result.groups()).containsExactly("admin");
        assertThat(result.authMethod()).isEqualTo("webauthn");
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(result.consumed()).isFalse();
    }

    @Test
    void roundtrip_consumed() {
        var record = new RefreshTokenRecord(
                "tok-used", "fam-2", "bob", "tenant-1",
                Set.of(), "password",
                Instant.now(), Instant.now().plusSeconds(3600),
                true);

        RefreshTokenEntity entity = RefreshTokenEntity.fromRecord(record);
        RefreshTokenRecord result = entity.toRecord();

        assertThat(result.consumed()).isTrue();
        assertThat(result.groups()).isEmpty();
    }
}
