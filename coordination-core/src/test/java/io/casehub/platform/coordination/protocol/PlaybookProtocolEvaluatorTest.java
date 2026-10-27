package io.casehub.platform.coordination.protocol;

import io.casehub.platform.coordination.CoordinationStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookProtocolEvaluatorTest {

    private PlaybookProtocolEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PlaybookProtocolEvaluator(CoordinationStateStore.noOp());
        evaluator.register("request-response", requestResponseStates());
    }

    @Test
    void evaluateReturnsEmptyForNonAdvisoryState() {
        var key = new ProtocolInstanceKey("request-response", "ch1");
        var result = evaluator.evaluate(key, "QUERY", Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void evaluateReturnsAdvisoryWhenStateHasOne() {
        var key = new ProtocolInstanceKey("request-response", "ch1");
        evaluator.evaluate(key, "QUERY", Map.of());
        var result = evaluator.evaluate(key, "QUERY", Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).severity()).isEqualTo(AdvisorySeverity.WARNING);
        assertThat(result.get(0).suggestedAction()).isEqualTo(SuggestedAction.INVESTIGATE);
    }

    @Test
    void evaluateTransitionsBackToNonAdvisory() {
        var key = new ProtocolInstanceKey("request-response", "ch1");
        evaluator.evaluate(key, "QUERY", Map.of());
        evaluator.evaluate(key, "QUERY", Map.of());
        var result = evaluator.evaluate(key, "RESPONSE", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void separateChannelsHaveIndependentState() {
        var ch1 = new ProtocolInstanceKey("request-response", "ch1");
        var ch2 = new ProtocolInstanceKey("request-response", "ch2");

        evaluator.evaluate(ch1, "QUERY", Map.of());
        evaluator.evaluate(ch1, "QUERY", Map.of());

        var r1 = evaluator.evaluate(ch1, "QUERY", Map.of());
        var r2 = evaluator.evaluate(ch2, "QUERY", Map.of());

        assertThat(r1).hasSize(1);
        assertThat(r2).isEmpty();
    }

    @Test
    void unknownProtocolThrows() {
        var key = new ProtocolInstanceKey("nonexistent", "ch1");
        assertThatThrownBy(() -> evaluator.evaluate(key, "QUERY", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonexistent");
    }

    @Test
    void unknownEventDoesNotTransition() {
        var key = new ProtocolInstanceKey("request-response", "ch1");
        evaluator.evaluate(key, "QUERY", Map.of());
        var result = evaluator.evaluate(key, "UNKNOWN", Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void evictRemovesInstance() {
        var key = new ProtocolInstanceKey("request-response", "ch1");
        evaluator.evaluate(key, "QUERY", Map.of());
        evaluator.evaluate(key, "QUERY", Map.of());

        evaluator.evict(key);

        var result = evaluator.evaluate(key, "QUERY", Map.of());
        assertThat(result).isEmpty();
    }

    private static Map<String, Object> requestResponseStates() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(
            Map.of("on", Map.of("QUERY", "AWAITING", "RESPONSE", "IDLE"))
        ));
        states.put("AWAITING", List.of(
            Map.of("on", Map.of("RESPONSE", "IDLE", "QUERY", "OVERLOADED"))
        ));
        states.put("OVERLOADED", List.of(
            Map.of("advisory", Map.of("severity", "WARNING", "message", "overloaded", "action", "INVESTIGATE")),
            Map.of("on", Map.of("RESPONSE", "AWAITING", "QUERY", "OVERLOADED"))
        ));
        return states;
    }
}
