package io.casehub.platform.authn.session.jpa;

import io.casehub.platform.api.authn.SessionRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEntityTest {

    @Test
    void roundtrip_allFields() {
        var record = new SessionRecord(
                "sess-1", "alice", "tenant-1",
                Set.of("admin", "user"), "webauthn",
                "csrf-abc", Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"), "fp-xyz");

        SessionEntity entity = SessionEntity.fromRecord(record);
        SessionRecord result = entity.toRecord();

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.actorId()).isEqualTo("alice");
        assertThat(result.tenancyId()).isEqualTo("tenant-1");
        assertThat(result.groups()).containsExactlyInAnyOrder("admin", "user");
        assertThat(result.authMethod()).isEqualTo("webauthn");
        assertThat(result.csrfToken()).isEqualTo("csrf-abc");
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(result.deviceFingerprint()).isEqualTo("fp-xyz");
    }

    @Test
    void roundtrip_emptyGroups() {
        var record = new SessionRecord(
                "sess-2", "bob", "tenant-1",
                Set.of(), "password",
                null, Instant.now(), Instant.now().plusSeconds(3600), null);

        SessionEntity entity = SessionEntity.fromRecord(record);
        assertThat(entity.groupsJson).isNull();

        SessionRecord result = entity.toRecord();
        assertThat(result.groups()).isEmpty();
    }

    @Test
    void roundtrip_nullGroups() {
        var record = new SessionRecord(
                "sess-3", "carol", "tenant-1",
                null, "password",
                null, Instant.now(), Instant.now().plusSeconds(3600), null);

        SessionEntity entity = SessionEntity.fromRecord(record);
        SessionRecord result = entity.toRecord();
        assertThat(result.groups()).isEmpty();
    }

    @Test
    void groupsToString_preservesValues() {
        assertThat(SessionEntity.groupsToString(new LinkedHashSet<>(java.util.List.of("a", "b"))))
                .isEqualTo("a,b");
    }

    @Test
    void stringToGroups_handlesWhitespace() {
        assertThat(SessionEntity.stringToGroups(" a , b , "))
                .containsExactlyInAnyOrder("a", "b");
    }
}
