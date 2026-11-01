package io.casehub.platform.registration;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRecordTest {

    @Test
    void nullIdThrows() {
        assertThatThrownBy(() -> new UserRecord(
                null, "a@b.com", "A", "t1", UserStatus.ACTIVE, "password", Instant.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
    }

    @Test
    void nullEmailThrows() {
        assertThatThrownBy(() -> new UserRecord(
                "id1", null, "A", "t1", UserStatus.ACTIVE, "password", Instant.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("email");
    }

    @Test
    void nullTenancyIdThrows() {
        assertThatThrownBy(() -> new UserRecord(
                "id1", "a@b.com", "A", null, UserStatus.ACTIVE, "password", Instant.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tenancyId");
    }

    @Test
    void nullStatusThrows() {
        assertThatThrownBy(() -> new UserRecord(
                "id1", "a@b.com", "A", "t1", null, "password", Instant.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status");
    }

    @Test
    void nullRegistrationMethodThrows() {
        assertThatThrownBy(() -> new UserRecord(
                "id1", "a@b.com", "A", "t1", UserStatus.ACTIVE, null, Instant.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("registrationMethod");
    }

    @Test
    void canonicalConstruction() {
        var now = Instant.now();
        var record = new UserRecord("id1", "a@b.com", "Alice", "t1",
                UserStatus.ACTIVE, "google", now, now);

        assertThat(record.id()).isEqualTo("id1");
        assertThat(record.email()).isEqualTo("a@b.com");
        assertThat(record.displayName()).isEqualTo("Alice");
        assertThat(record.tenancyId()).isEqualTo("t1");
        assertThat(record.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(record.registrationMethod()).isEqualTo("google");
        assertThat(record.createdAt()).isEqualTo(now);
        assertThat(record.activatedAt()).isEqualTo(now);
    }

    @Test
    void nullableFieldsAccepted() {
        var record = new UserRecord("id1", "a@b.com", null, "t1",
                UserStatus.PENDING_VERIFICATION, "password", null, null);

        assertThat(record.displayName()).isNull();
        assertThat(record.createdAt()).isNull();
        assertThat(record.activatedAt()).isNull();
    }
}
