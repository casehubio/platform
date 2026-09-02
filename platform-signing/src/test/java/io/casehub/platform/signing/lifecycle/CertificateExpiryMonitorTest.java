package io.casehub.platform.signing.lifecycle;

import io.casehub.platform.signing.document.KeyStoreManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateExpiryMonitorTest {

    @TempDir
    Path tempDir;

    @Test
    void check_validCert_noEvents() throws Exception {
        Path ks = io.casehub.platform.signing.document.TestKeyStoreHelper.createTestKeystore(tempDir);
        var mgr = new KeyStoreManager(ks.toString(), "changeit", "PKCS12", "test-seal");
        List<CertificateExpiryEvent> events = new ArrayList<>();

        var monitor = new CertificateExpiryMonitor(mgr, 30, Clock.systemUTC(), events::add);
        monitor.check();

        assertThat(events).isEmpty();
    }

    @Test
    void check_certExpiringWithinThreshold_firesEvent() throws Exception {
        Path ks = io.casehub.platform.signing.document.TestKeyStoreHelper.createTestKeystore(tempDir);
        var mgr = new KeyStoreManager(ks.toString(), "changeit", "PKCS12", "test-seal");

        // Advance clock to within 30 days of cert expiry (cert valid for 365 days from creation)
        Instant nearExpiry = Instant.now().plus(Duration.ofDays(340));
        Clock futureClock = Clock.fixed(nearExpiry, ZoneId.of("UTC"));

        List<CertificateExpiryEvent> events = new ArrayList<>();
        var monitor = new CertificateExpiryMonitor(mgr, 30, futureClock, events::add);
        monitor.check();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().alias()).contains("Test Seal");
        assertThat(events.getFirst().daysUntilExpiry()).isLessThanOrEqualTo(30);
    }

    @Test
    void check_certAlreadyExpired_firesEvent() throws Exception {
        Path ks = io.casehub.platform.signing.document.TestKeyStoreHelper.createTestKeystore(tempDir);
        var mgr = new KeyStoreManager(ks.toString(), "changeit", "PKCS12", "test-seal");

        Instant pastExpiry = Instant.now().plus(Duration.ofDays(400));
        Clock futureClock = Clock.fixed(pastExpiry, ZoneId.of("UTC"));

        List<CertificateExpiryEvent> events = new ArrayList<>();
        var monitor = new CertificateExpiryMonitor(mgr, 30, futureClock, events::add);
        monitor.check();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().daysUntilExpiry()).isLessThan(0);
        assertThat(events.getFirst().expired()).isTrue();
    }

    @Test
    void check_noKeystore_noEvents() {
        var mgr = new KeyStoreManager(null, null, "PKCS12", null);
        List<CertificateExpiryEvent> events = new ArrayList<>();

        var monitor = new CertificateExpiryMonitor(mgr, 30, Clock.systemUTC(), events::add);
        monitor.check();

        assertThat(events).isEmpty();
    }
}
