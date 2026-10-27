package io.casehub.platform.coordination.protocol;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompiledProtocolTest {

    @Test
    void compilesSimpleStateMachine() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(Map.of("on", Map.of("QUERY", "ACTIVE"))));
        states.put("ACTIVE", List.of(Map.of("on", Map.of("RESPONSE", "IDLE"))));

        var protocol = CompiledProtocol.from("test", states);

        assertThat(protocol.initialState()).isEqualTo("IDLE");
        assertThat(protocol.advisoryMap()).isEmpty();

        var sm = protocol.buildStateMachine();
        assertThat(sm.currentState()).isEqualTo("IDLE");

        var router = protocol.buildRouter(sm);
        assertThat(router.fire("QUERY")).isTrue();
        assertThat(sm.currentState()).isEqualTo("ACTIVE");
        assertThat(router.fire("RESPONSE")).isTrue();
        assertThat(sm.currentState()).isEqualTo("IDLE");
    }

    @Test
    void extractsAdvisoryMetadata() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(Map.of("on", Map.of("QUERY", "WARN"))));
        states.put("WARN", List.of(
            Map.of("advisory", Map.of(
                "severity", "WARNING",
                "message", "too many queries",
                "action", "INVESTIGATE")),
            Map.of("on", Map.of("RESPONSE", "IDLE"))
        ));

        var protocol = CompiledProtocol.from("test", states);

        assertThat(protocol.advisoryMap()).containsKey("WARN");
        var advisory = protocol.advisoryMap().get("WARN");
        assertThat(advisory.severity()).isEqualTo(AdvisorySeverity.WARNING);
        assertThat(advisory.message()).isEqualTo("too many queries");
        assertThat(advisory.suggestedAction()).isEqualTo(SuggestedAction.INVESTIGATE);
    }

    @Test
    void unknownEventDoesNotTransition() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(Map.of("on", Map.of("QUERY", "ACTIVE"))));
        states.put("ACTIVE", List.of(Map.of("on", Map.of("RESPONSE", "IDLE"))));

        var protocol = CompiledProtocol.from("test", states);
        var sm = protocol.buildStateMachine();
        var router = protocol.buildRouter(sm);

        assertThat(router.fire("UNKNOWN")).isFalse();
        assertThat(sm.currentState()).isEqualTo("IDLE");
    }

    @Test
    void invalidSeverityThrows() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(
            Map.of("advisory", Map.of(
                "severity", "BOGUS",
                "message", "test",
                "action", "LOG"))
        ));

        assertThatThrownBy(() -> CompiledProtocol.from("test", states))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BOGUS");
    }

    @Test
    void multipleStatesWithAdvisories() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(Map.of("on", Map.of("A", "WARN", "B", "CRIT"))));
        states.put("WARN", List.of(
            Map.of("advisory", Map.of("severity", "WARNING", "message", "w", "action", "LOG")),
            Map.of("on", Map.of("C", "IDLE"))
        ));
        states.put("CRIT", List.of(
            Map.of("advisory", Map.of("severity", "CRITICAL", "message", "c", "action", "ESCALATE")),
            Map.of("on", Map.of("C", "IDLE"))
        ));

        var protocol = CompiledProtocol.from("test", states);
        assertThat(protocol.advisoryMap()).hasSize(2);
        assertThat(protocol.advisoryMap().get("WARN").severity()).isEqualTo(AdvisorySeverity.WARNING);
        assertThat(protocol.advisoryMap().get("CRIT").severity()).isEqualTo(AdvisorySeverity.CRITICAL);
    }

    @Test
    void buildStateMachineWithOverrideInitialState() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(Map.of("on", Map.of("QUERY", "ACTIVE"))));
        states.put("ACTIVE", List.of(Map.of("on", Map.of("RESPONSE", "IDLE"))));

        var protocol = CompiledProtocol.from("test", states);
        var sm = protocol.buildStateMachine("ACTIVE");
        assertThat(sm.currentState()).isEqualTo("ACTIVE");

        var router = protocol.buildRouter(sm);
        assertThat(router.fire("RESPONSE")).isTrue();
        assertThat(sm.currentState()).isEqualTo("IDLE");
    }
}
