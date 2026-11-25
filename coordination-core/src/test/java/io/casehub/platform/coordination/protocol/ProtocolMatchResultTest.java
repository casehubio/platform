package io.casehub.platform.coordination.protocol;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolMatchResultTest {

    @Test
    void matchedHoldsTemplateNameAndParams() {
        var result = new ProtocolMatchResult.Matched("vote", Map.of("decision", "approve"));
        assertThat(result.templateName()).isEqualTo("vote");
        assertThat(result.params()).containsEntry("decision", "approve");
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void matchedWithExplicitScore() {
        var result = new ProtocolMatchResult.Matched("vote", Map.of("decision", "approve"), 0.85);
        assertThat(result.templateName()).isEqualTo("vote");
        assertThat(result.params()).containsEntry("decision", "approve");
        assertThat(result.score()).isEqualTo(0.85);
    }

    @Test
    void matchedScoreOutOfRangeThrows() {
        assertThatThrownBy(() -> new ProtocolMatchResult.Matched("vote", Map.of(), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProtocolMatchResult.Matched("vote", Map.of(), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchedParamsAreUnmodifiable() {
        var result = new ProtocolMatchResult.Matched("vote", Map.of("decision", "approve"));
        assertThatThrownBy(() -> result.params().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unmatchedHoldsOriginalResponse() {
        var result = new ProtocolMatchResult.Unmatched("some random text");
        assertThat(result.response()).isEqualTo("some random text");
    }

    @Test
    void sealedTypeExhaustivenessViaSwitchExpression() {
        ProtocolMatchResult result = new ProtocolMatchResult.Matched("vote", Map.of());
        String outcome = switch (result) {
            case ProtocolMatchResult.Matched m -> "matched: " + m.templateName();
            case ProtocolMatchResult.Unmatched u -> "unmatched: " + u.response();
        };
        assertThat(outcome).isEqualTo("matched: vote");
    }
}
