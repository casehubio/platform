package io.casehub.platform.simulation.testing;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialCorpusTest {

    @Test
    void resolveReturnsPreConfiguredSeed() {
        var seed = CredentialCorpus.resolve("tenant-1");
        assertThat(seed.qualifiedName()).isEqualTo("credential-resolver.resolve");
        assertThat(seed.keyExtractor()).isNotNull();
    }

    @Test
    void credentialSingleEntry() {
        Map<String, String> cred = CredentialCorpus.credential("token", "abc123");
        assertThat(cred).containsEntry("token", "abc123");
        assertThat(cred).hasSize(1);
    }

    @Test
    void credentialTwoEntries() {
        Map<String, String> cred = CredentialCorpus.credential("user", "admin", "password", "secret");
        assertThat(cred).containsEntry("user", "admin");
        assertThat(cred).containsEntry("password", "secret");
    }

    @Test
    void keyExtractorUsesRefDirectly() {
        var seed = CredentialCorpus.resolve("tenant-1");
        seed.add("db-credentials", CredentialCorpus.credential("user", "admin"));
        assertThat(seed.build().get(0).key()).isEqualTo("db-credentials");
    }
}
