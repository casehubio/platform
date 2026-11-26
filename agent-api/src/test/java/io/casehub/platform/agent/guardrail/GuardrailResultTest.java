package io.casehub.platform.agent.guardrail;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardrailResultTest {

    @Test
    void passIsPass() {
        var result = new GuardrailResult.Pass();
        assertInstanceOf(GuardrailResult.Pass.class, result);
    }

    @Test
    void failCarriesReason() {
        var result = new GuardrailResult.Fail("toxic content");
        assertEquals("toxic content", result.reason());
    }

    @Test
    void repromptCarriesMessage() {
        var result = new GuardrailResult.Reprompt("Please rephrase without PII");
        assertEquals("Please rephrase without PII", result.repromptMessage());
    }

    @Test
    void exhaustiveSwitchCompiles() {
        GuardrailResult result = new GuardrailResult.Pass();
        String outcome = switch (result) {
            case GuardrailResult.Pass p -> "pass";
            case GuardrailResult.Fail f -> "fail: " + f.reason();
            case GuardrailResult.Reprompt r -> "reprompt: " + r.repromptMessage();
        };
        assertEquals("pass", outcome);
    }

    @Test
    void guardrailExceptionCarriesReason() {
        var ex = new GuardrailException("blocked");
        assertEquals("blocked", ex.getMessage());
    }

    @Test
    void inputGuardrailIsFunctionalInterface() {
        InputGuardrail guardrail = config -> new GuardrailResult.Pass();
        var result = guardrail.validate(null);
        assertInstanceOf(GuardrailResult.Pass.class, result);
    }

    @Test
    void outputGuardrailIsFunctionalInterface() {
        OutputGuardrail guardrail = (response, config) -> new GuardrailResult.Fail("bad");
        var result = guardrail.validate("test response", null);
        assertInstanceOf(GuardrailResult.Fail.class, result);
        assertEquals("bad", ((GuardrailResult.Fail) result).reason());
    }
}
