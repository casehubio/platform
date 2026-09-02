package io.casehub.platform.signing.lifecycle;

import io.casehub.platform.signing.document.KeyStoreManager;
import io.casehub.platform.signing.document.TestKeyStoreHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KeyStoreRotationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rotate_replacesManager() throws Exception {
        Path ks = TestKeyStoreHelper.createTestKeystore(tempDir);
        AtomicReference<KeyStoreManager> holder = new AtomicReference<>(
                new KeyStoreManager(ks.toString(), TestKeyStoreHelper.PASSWORD,
                        "PKCS12", TestKeyStoreHelper.ALIAS));

        var service = new KeyStoreRotationService(holder);
        var original = holder.get();
        assertThat(original.isLoaded()).isTrue();

        Path newKs = TestKeyStoreHelper.createTestKeystore(
                Files.createDirectories(tempDir.resolve("rotated")));
        Files.copy(newKs, ks, StandardCopyOption.REPLACE_EXISTING);

        service.rotate(ks.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);

        assertThat(holder.get()).isNotSameAs(original);
        assertThat(holder.get().isLoaded()).isTrue();
    }

    @Test
    void rotate_invalidKeystore_keepsOld() throws Exception {
        Path ks = TestKeyStoreHelper.createTestKeystore(tempDir);
        AtomicReference<KeyStoreManager> holder = new AtomicReference<>(
                new KeyStoreManager(ks.toString(), TestKeyStoreHelper.PASSWORD,
                        "PKCS12", TestKeyStoreHelper.ALIAS));

        var service = new KeyStoreRotationService(holder);
        var original = holder.get();

        service.rotate(ks.toString(), "wrong-password", "PKCS12", TestKeyStoreHelper.ALIAS);

        assertThat(holder.get()).isSameAs(original);
    }

    @Test
    void rotate_missingPath_keepsOld() throws Exception {
        Path ks = TestKeyStoreHelper.createTestKeystore(tempDir);
        AtomicReference<KeyStoreManager> holder = new AtomicReference<>(
                new KeyStoreManager(ks.toString(), TestKeyStoreHelper.PASSWORD,
                        "PKCS12", TestKeyStoreHelper.ALIAS));

        var service = new KeyStoreRotationService(holder);
        var original = holder.get();

        service.rotate("/nonexistent/path.p12", TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);

        assertThat(holder.get()).isSameAs(original);
    }
}
