package io.casehub.platform.governance;

import io.casehub.platform.api.governance.TriageContext;
import io.casehub.platform.api.governance.TriageDecision;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpTriageHandlerTest {

    @Test
    void evaluate_alwaysReturnsAbandon() {
        var handler = new NoOpTriageHandler();
        var context = new TriageContext("op-1", "playbook", 1, 2,
                "step failed", Map.of(), Map.of());

        var decision = handler.evaluate(context);

        assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
        assertThat(((TriageDecision.Abandon) decision).reason()).isNotBlank();
    }
}
