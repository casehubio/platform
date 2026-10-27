package io.casehub.platform.coordination.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProtocolAdvisoryTest {

    @Test
    void advisorySeverityValues() {
        assertThat(AdvisorySeverity.values())
            .containsExactly(AdvisorySeverity.INFO, AdvisorySeverity.WARNING, AdvisorySeverity.CRITICAL);
    }

    @Test
    void suggestedActionValues() {
        assertThat(SuggestedAction.values())
            .containsExactly(SuggestedAction.LOG, SuggestedAction.ESCALATE,
                             SuggestedAction.INVESTIGATE, SuggestedAction.REROUTE);
    }

    @Test
    void protocolAdvisoryRecordAccessors() {
        var advisory = new ProtocolAdvisory(AdvisorySeverity.WARNING, "test message", SuggestedAction.ESCALATE);
        assertThat(advisory.severity()).isEqualTo(AdvisorySeverity.WARNING);
        assertThat(advisory.message()).isEqualTo("test message");
        assertThat(advisory.suggestedAction()).isEqualTo(SuggestedAction.ESCALATE);
    }

    @Test
    void instanceKeyStoreKey() {
        var key = new ProtocolInstanceKey("request-response", "channel-42");
        assertThat(key.storeKey()).isEqualTo("request-response:channel-42");
    }

    @Test
    void instanceKeyEquality() {
        var a = new ProtocolInstanceKey("proto", "ch1");
        var b = new ProtocolInstanceKey("proto", "ch1");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
