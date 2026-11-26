package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuardrailStreamOperatorTest {

    private static final AgentSessionConfig CONFIG = AgentSessionConfig.of("sys", "user");

    private static AgentEvent.InvocationComplete complete() {
        return new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, 0.001, 100, 90, null, 1, false, "claude", null);
    }

    @Test
    void passGuardrailReplaysAllEvents() {
        var events = List.<AgentEvent>of(
            new AgentEvent.TextDelta("Hello "),
            new AgentEvent.TextDelta("world"),
            complete()
        );
        OutputGuardrail pass = (response, config) -> new GuardrailResult.Pass();

        var result = GuardrailStreamOperator.validate(
            Multi.createFrom().iterable(events),
            List.of(pass), CONFIG, 3, reprompt -> Multi.createFrom().empty()
        ).collect().asList().await().indefinitely();

        assertEquals(3, result.size());
        assertInstanceOf(AgentEvent.TextDelta.class, result.get(0));
        assertEquals("Hello ", ((AgentEvent.TextDelta) result.get(0)).text());
    }

    @Test
    void failGuardrailEmitsFailure() {
        var events = List.<AgentEvent>of(
            new AgentEvent.TextDelta("bad content"),
            complete()
        );
        OutputGuardrail block = (response, config) -> new GuardrailResult.Fail("toxic");

        assertThrows(GuardrailException.class, () ->
            GuardrailStreamOperator.validate(
                Multi.createFrom().iterable(events),
                List.of(block), CONFIG, 3, reprompt -> Multi.createFrom().empty()
            ).collect().asList().await().indefinitely()
        );
    }

    @Test
    void repromptRetries() {
        var callCount = new AtomicInteger(0);
        var events = List.<AgentEvent>of(
            new AgentEvent.TextDelta("attempt"),
            complete()
        );
        OutputGuardrail repromptOnce = (response, config) -> {
            if (callCount.getAndIncrement() == 0) {
                return new GuardrailResult.Reprompt("try again");
            }
            return new GuardrailResult.Pass();
        };

        var goodEvents = List.<AgentEvent>of(
            new AgentEvent.TextDelta("good"),
            complete()
        );

        var result = GuardrailStreamOperator.validate(
            Multi.createFrom().iterable(events),
            List.of(repromptOnce), CONFIG, 3,
            reprompt -> Multi.createFrom().iterable(goodEvents)
        ).collect().asList().await().indefinitely();

        assertEquals(2, result.size());
        assertEquals("good", ((AgentEvent.TextDelta) result.get(0)).text());
        assertEquals(2, callCount.get());
    }

    @Test
    void maxRepromptExceededThrows() {
        OutputGuardrail alwaysReprompt = (response, config) -> new GuardrailResult.Reprompt("try again");
        var events = List.<AgentEvent>of(new AgentEvent.TextDelta("bad"), complete());

        var ex = assertThrows(GuardrailException.class, () ->
            GuardrailStreamOperator.validate(
                Multi.createFrom().iterable(events),
                List.of(alwaysReprompt), CONFIG, 0,
                reprompt -> Multi.createFrom().iterable(events)
            ).collect().asList().await().indefinitely()
        );
        assertTrue(ex.getMessage().contains("Max reprompts exceeded"));
    }

    @Test
    void emptyGuardrailListPassesThrough() {
        var events = List.<AgentEvent>of(new AgentEvent.TextDelta("hello"), complete());

        var result = GuardrailStreamOperator.validate(
            Multi.createFrom().iterable(events),
            List.of(), CONFIG, 3, reprompt -> Multi.createFrom().empty()
        ).collect().asList().await().indefinitely();

        assertEquals(2, result.size());
    }

    @Test
    void concatenatesTextFromMultipleDeltas() {
        var callCount = new AtomicInteger(0);
        OutputGuardrail checkFull = (response, config) -> {
            callCount.incrementAndGet();
            assertEquals("Hello world", response);
            return new GuardrailResult.Pass();
        };

        var events = List.<AgentEvent>of(
            new AgentEvent.TextDelta("Hello "),
            new AgentEvent.TextDelta("world"),
            complete()
        );

        GuardrailStreamOperator.validate(
            Multi.createFrom().iterable(events),
            List.of(checkFull), CONFIG, 3, reprompt -> Multi.createFrom().empty()
        ).collect().asList().await().indefinitely();

        assertEquals(1, callCount.get());
    }
}
