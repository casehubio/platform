package io.casehub.platform.signing.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedListManagerTest {

    @Test
    void disabled_whenNoUrl() {
        var mgr = new TrustedListManager((String) null);
        assertThat(mgr.isEnabled()).isFalse();
        assertThat(mgr.getTrustedListSource().getCertificates()).isEmpty();
    }

    @Test
    void disabled_whenBlankUrl() {
        var mgr = new TrustedListManager("  ");
        assertThat(mgr.isEnabled()).isFalse();
    }

    @Test
    void enabled_whenUrlSet() {
        var mgr = new TrustedListManager("https://ec.europa.eu/tools/lotl/eu-lotl.xml");
        assertThat(mgr.isEnabled()).isTrue();
    }
}
