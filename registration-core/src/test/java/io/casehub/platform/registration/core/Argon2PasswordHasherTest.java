package io.casehub.platform.registration.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(65536, 1, 1);

    @Test
    void hashReturnsNonEmpty() {
        String hashed = hasher.hash("password123");
        assertThat(hashed).isNotNull().isNotEmpty();
    }

    @Test
    void verifyCorrectPassword() {
        String hashed = hasher.hash("correctPassword");
        assertThat(hasher.verify("correctPassword", hashed)).isTrue();
    }

    @Test
    void verifyWrongPassword() {
        String hashed = hasher.hash("correctPassword");
        assertThat(hasher.verify("wrongPassword", hashed)).isFalse();
    }

    @Test
    void hashIsNonDeterministic() {
        String hash1 = hasher.hash("sameInput");
        String hash2 = hasher.hash("sameInput");
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
