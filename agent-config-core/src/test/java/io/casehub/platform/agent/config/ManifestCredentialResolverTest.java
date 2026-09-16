package io.casehub.platform.agent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestCredentialResolverTest {

    private final ManifestCredentialResolver resolver = new ManifestCredentialResolver(ref -> Map.of());

    @Test
    void resolvesFileRef(@TempDir Path tempDir) throws IOException {
        var keyFile = tempDir.resolve("api-key.txt");
        Files.writeString(keyFile, "  sk-file-test-123  \n");
        var ref = new CredentialRef.FileRef(keyFile.toString());
        assertThat(resolver.resolve(ref)).isEqualTo("sk-file-test-123");
    }

    @Test
    void fileRefTrimsWhitespace(@TempDir Path tempDir) throws IOException {
        var keyFile = tempDir.resolve("key.txt");
        Files.writeString(keyFile, "\n  value  \n");
        assertThat(resolver.resolve(new CredentialRef.FileRef(keyFile.toString()))).isEqualTo("value");
    }

    @Test
    void fileRefThrowsForMissingFile() {
        var ref = new CredentialRef.FileRef("/nonexistent/path/key.txt");
        assertThatThrownBy(() -> resolver.resolve(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read credential file");
    }

    @Test
    void externalRefDelegatesToResolver() {
        var resolver = new ManifestCredentialResolver(
                ref -> "vault/anthropic".equals(ref)
                        ? Map.of("api-key", "sk-vault-123")
                        : Map.of());
        var ref = new CredentialRef.ExternalRef("vault/anthropic");
        assertThat(resolver.resolve(ref)).isEqualTo("sk-vault-123");
    }

    @Test
    void externalRefThrowsWhenNotFound() {
        var ref = new CredentialRef.ExternalRef("vault/nonexistent");
        assertThatThrownBy(() -> resolver.resolve(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Credential ref not found");
    }

    @Test
    void resolveMapResolvesAllEntries(@TempDir Path tempDir) throws IOException {
        var keyFile = tempDir.resolve("key.txt");
        Files.writeString(keyFile, "file-value");
        var refs = Map.of(
                "field-a", "file:" + keyFile,
                "field-b", "ref:vault/secret");
        var resolver = new ManifestCredentialResolver(
                ref -> "vault/secret".equals(ref) ? Map.of("k", "vault-value") : Map.of());
        var result = resolver.resolveMap(refs);
        assertThat(result).containsEntry("field-a", "file-value");
        assertThat(result).containsEntry("field-b", "vault-value");
    }
}
