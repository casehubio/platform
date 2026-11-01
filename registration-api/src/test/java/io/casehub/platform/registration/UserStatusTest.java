package io.casehub.platform.registration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @Test
    void allFiveValuesExist() {
        assertThat(UserStatus.values()).hasSize(5);
        assertThat(UserStatus.valueOf("PENDING_VERIFICATION")).isNotNull();
        assertThat(UserStatus.valueOf("PENDING_APPROVAL")).isNotNull();
        assertThat(UserStatus.valueOf("ACTIVE")).isNotNull();
        assertThat(UserStatus.valueOf("REJECTED")).isNotNull();
        assertThat(UserStatus.valueOf("SUSPENDED")).isNotNull();
    }
}
