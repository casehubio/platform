package io.casehub.platform.api.credentials;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialPropertyKeysTest {

    @Test
    void constants_use_kebab_case() {
        assertThat(CredentialPropertyKeys.USER).isEqualTo("user");
        assertThat(CredentialPropertyKeys.PASSWORD).isEqualTo("password");
        assertThat(CredentialPropertyKeys.BEARER_TOKEN).isEqualTo("bearer-token");
        assertThat(CredentialPropertyKeys.API_KEY).isEqualTo("api-key");
        assertThat(CredentialPropertyKeys.EXPIRES_AT).isEqualTo("expires-at");
    }

    @Test
    void constants_are_distinct() {
        assertThat(java.util.Set.of(
                CredentialPropertyKeys.USER,
                CredentialPropertyKeys.PASSWORD,
                CredentialPropertyKeys.BEARER_TOKEN,
                CredentialPropertyKeys.API_KEY,
                CredentialPropertyKeys.EXPIRES_AT
        )).hasSize(5);
    }
}
