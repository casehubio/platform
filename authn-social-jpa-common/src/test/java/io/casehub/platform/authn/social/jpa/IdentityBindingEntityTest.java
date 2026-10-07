package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.IdentityBinding;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityBindingEntityTest {

    @Test
    void roundtrip() {
        var binding = new IdentityBinding(
                "google", "ext-123", "alice", "tenant-1", "alice@example.com",
                Instant.parse("2026-01-15T10:30:00Z"));

        IdentityBindingEntity entity = IdentityBindingEntity.fromRecord(binding);
        IdentityBinding result = entity.toRecord();

        assertThat(result.provider()).isEqualTo("google");
        assertThat(result.externalId()).isEqualTo("ext-123");
        assertThat(result.actorId()).isEqualTo("alice");
        assertThat(result.tenancyId()).isEqualTo("tenant-1");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-01-15T10:30:00Z"));
    }

    @Test
    void roundtrip_nullEmail() {
        var binding = new IdentityBinding(
                "github", "gh-456", "bob", "tenant-2", null,
                Instant.parse("2026-02-20T14:00:00Z"));

        IdentityBindingEntity entity = IdentityBindingEntity.fromRecord(binding);
        IdentityBinding result = entity.toRecord();

        assertThat(result.email()).isNull();
        assertThat(result.provider()).isEqualTo("github");
    }

    @Test
    void roundtrip_nullCreatedAt_usesNow() {
        var binding = new IdentityBinding("apple", "ap-789", "carol", "tenant-3", null, null);

        IdentityBindingEntity entity = IdentityBindingEntity.fromRecord(binding);

        assertThat(entity.createdAt).isNotNull();
    }
}
