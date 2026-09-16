package io.casehub.platform.agent.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialRefTest {

    @Test
    void parsesEnvRef() {
        var ref = CredentialRef.parse("env:MY_KEY");
        assertThat(ref).isInstanceOf(CredentialRef.EnvRef.class);
        assertThat(((CredentialRef.EnvRef) ref).variableName()).isEqualTo("MY_KEY");
    }

    @Test
    void parsesFileRef() {
        var ref = CredentialRef.parse("file:/tmp/key.txt");
        assertThat(ref).isInstanceOf(CredentialRef.FileRef.class);
        assertThat(((CredentialRef.FileRef) ref).path()).isEqualTo("/tmp/key.txt");
    }

    @Test
    void parsesExternalRef() {
        var ref = CredentialRef.parse("ref:vault/my-secret");
        assertThat(ref).isInstanceOf(CredentialRef.ExternalRef.class);
        assertThat(((CredentialRef.ExternalRef) ref).credentialRef()).isEqualTo("vault/my-secret");
    }

    @Test
    void rejectsRawValue() {
        assertThatThrownBy(() -> CredentialRef.parse("sk-ant-1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with env:, file:, or ref:");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> CredentialRef.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> CredentialRef.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
