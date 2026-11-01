package io.casehub.platform.registration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRegistrationRequestTest {

    @Test
    void nullEmailThrows() {
        assertThatThrownBy(() -> new PasswordRegistrationRequest(
                null, "pass123", "Alice", "t1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("email");
    }

    @Test
    void nullPasswordThrows() {
        assertThatThrownBy(() -> new PasswordRegistrationRequest(
                "a@b.com", null, "Alice", "t1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("password");
    }

    @Test
    void nullTenancyIdThrows() {
        assertThatThrownBy(() -> new PasswordRegistrationRequest(
                "a@b.com", "pass123", "Alice", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tenancyId");
    }

    @Test
    void toStringExcludesPassword() {
        var request = new PasswordRegistrationRequest(
                "a@b.com", "supersecret", "Alice", "t1", null);

        assertThat(request.toString()).doesNotContain("supersecret");
        assertThat(request.toString()).contains("a@b.com");
        assertThat(request.toString()).contains("t1");
    }

    @Test
    void nullableFieldsAccepted() {
        var request = new PasswordRegistrationRequest(
                "a@b.com", "pass123", null, "t1", null);

        assertThat(request.displayName()).isNull();
        assertThat(request.invitationToken()).isNull();
    }
}
