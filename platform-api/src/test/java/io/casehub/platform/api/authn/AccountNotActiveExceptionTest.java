package io.casehub.platform.api.authn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNotActiveExceptionTest {

    @Test
    void messageIncludesLowercaseStatusName() {
        var ex = new AccountNotActiveException("password", "PENDING_APPROVAL");
        assertThat(ex.getMessage()).isEqualTo("Account is pending_approval");
        assertThat(ex.method()).isEqualTo("password");
        assertThat(ex.statusName()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void extendsAuthenticationException() {
        var ex = new AccountNotActiveException("google", "SUSPENDED");
        assertThat(ex).isInstanceOf(AuthenticationException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
