package io.casehub.platform.signing.document;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyStoreManagerTest {

    @TempDir
    static Path tempDir;
    static Path keystorePath;

    @BeforeAll
    static void createKeystore() throws Exception {
        keystorePath = TestKeyStoreHelper.createTestKeystore(tempDir);
    }

    @Test
    void load_extractsPrivateKeyAndChain() {
        var mgr = new KeyStoreManager(
                keystorePath.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
        assertThat(mgr.isLoaded()).isTrue();
        var keys = mgr.getKeys();
        assertThat(keys).isNotEmpty();
        assertThat(keys.getFirst().getCertificate().getSubject()
                .getPrincipal().getName()).contains("CN=Test Seal");
    }

    @Test
    void load_missingPath_notLoaded() {
        var mgr = new KeyStoreManager(null, null, "PKCS12", null);
        assertThat(mgr.isLoaded()).isFalse();
        assertThat(mgr.getKeys()).isEmpty();
    }

    @Test
    void load_blankPath_notLoaded() {
        var mgr = new KeyStoreManager("  ", null, "PKCS12", null);
        assertThat(mgr.isLoaded()).isFalse();
    }

    @Test
    void load_wrongPassword_throws() {
        assertThatThrownBy(() -> new KeyStoreManager(
                keystorePath.toString(), "wrong", "PKCS12", TestKeyStoreHelper.ALIAS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolveKey_defaultAlias() {
        var mgr = new KeyStoreManager(
                keystorePath.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
        var key = mgr.resolveKey(null);
        assertThat(key).isNotNull();
        assertThat(key.getCertificate().getSubject()
                .getPrincipal().getName()).contains("CN=Test Seal");
    }

    @Test
    void resolveKey_unknownTenantFallsBackToDefault() {
        var mgr = new KeyStoreManager(
                keystorePath.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
        var key = mgr.resolveKey("nonexistent-tenant");
        assertThat(key).isNotNull();
        assertThat(key.getCertificate().getSubject()
                .getPrincipal().getName()).contains("CN=Test Seal");
    }

    @Test
    void resolveKey_notLoaded_returnsNull() {
        var mgr = new KeyStoreManager(null, null, "PKCS12", null);
        assertThat(mgr.resolveKey(null)).isNull();
    }
}
