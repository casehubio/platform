package io.casehub.platform.observability;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KeyStoreExpiryCheckerTest {

    @TempDir
    static Path tempDir;
    static String healthyKsPath;
    static String expiringKsPath;
    static String expiredKsPath;
    static final char[] PASSWORD = "test123".toCharArray();

    @BeforeAll
    static void createTestKeystores() throws Exception {
        healthyKsPath = generateKeystore("healthy", 365, null);
        expiringKsPath = generateKeystore("expiring", 10, null);
        expiredKsPath = generateKeystore("expired", 1, "-2d");
    }

    @Test
    void healthyKeystore() {
        var checker = new KeyStoreExpiryChecker(healthyKsPath, PASSWORD, "PKCS12", 30);
        var result = checker.check();
        assertThat(result.healthy()).isTrue();
        assertThat(result.certificates()).hasSize(1);
        assertThat(result.certificates().getFirst().expired()).isFalse();
        assertThat(result.certificates().getFirst().daysRemaining()).isGreaterThan(30);
    }

    @Test
    void expiringKeystore() {
        var checker = new KeyStoreExpiryChecker(expiringKsPath, PASSWORD, "PKCS12", 30);
        var result = checker.check();
        assertThat(result.healthy()).isFalse();
        assertThat(result.certificates().getFirst().expired()).isFalse();
        assertThat(result.certificates().getFirst().daysRemaining()).isLessThanOrEqualTo(10);
    }

    @Test
    void expiredKeystore() {
        var checker = new KeyStoreExpiryChecker(expiredKsPath, PASSWORD, "PKCS12", 30);
        var result = checker.check();
        assertThat(result.healthy()).isFalse();
        assertThat(result.certificates().getFirst().expired()).isTrue();
    }

    @Test
    void nullPathReturnsEmpty() {
        var checker = new KeyStoreExpiryChecker(null, PASSWORD, "PKCS12", 30);
        var result = checker.check();
        assertThat(result.healthy()).isTrue();
        assertThat(result.certificates()).isEmpty();
    }

    @Test
    void blankPathReturnsEmpty() {
        var checker = new KeyStoreExpiryChecker("  ", PASSWORD, "PKCS12", 30);
        var result = checker.check();
        assertThat(result.healthy()).isTrue();
        assertThat(result.certificates()).isEmpty();
    }

    private static String generateKeystore(String name, int validityDays, String startDate) throws Exception {
        var path = tempDir.resolve(name + ".p12").toString();
        var cmd = new java.util.ArrayList<String>();
        cmd.add("keytool");
        cmd.add("-genkeypair");
        cmd.add("-keystore"); cmd.add(path);
        cmd.add("-alias"); cmd.add(name);
        cmd.add("-keyalg"); cmd.add("RSA");
        cmd.add("-keysize"); cmd.add("2048");
        cmd.add("-dname"); cmd.add("CN=" + name + ",O=test");
        cmd.add("-storepass"); cmd.add(new String(PASSWORD));
        cmd.add("-storetype"); cmd.add("PKCS12");
        cmd.add("-validity"); cmd.add(String.valueOf(validityDays));
        if (startDate != null) {
            cmd.add("-startdate"); cmd.add(startDate);
        }
        var process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new RuntimeException("keytool failed: " + new String(process.getInputStream().readAllBytes()));
        }
        return path;
    }
}
