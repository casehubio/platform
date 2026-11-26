package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;

import java.util.List;
import java.util.function.Function;

public final class GuardrailStreamOperator {

    private GuardrailStreamOperator() {}

    public static Multi<AgentEvent> validate(
            Multi<AgentEvent> source,
            List<OutputGuardrail> guardrails,
            AgentSessionConfig config,
            int maxReprompts,
            Function<String, Multi<AgentEvent>> repromptFn) {

        if (guardrails.isEmpty()) {
            return source;
        }

        return source.collect().asList()
            .onItem().transformToMulti(events -> {
                String text = events.stream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .reduce("", String::concat);

                for (OutputGuardrail guardrail : guardrails) {
                    GuardrailResult result = guardrail.validate(text, config);
                    switch (result) {
                        case GuardrailResult.Pass p -> {}
                        case GuardrailResult.Fail f ->
                            throw new GuardrailException(f.reason());
                        case GuardrailResult.Reprompt r -> {
                            if (maxReprompts <= 0) {
                                throw new GuardrailException(
                                    "Max reprompts exceeded: " + r.repromptMessage());
                            }
                            return validate(
                                repromptFn.apply(r.repromptMessage()),
                                guardrails, config, maxReprompts - 1, repromptFn);
                        }
                    }
                }
                return Multi.createFrom().iterable(events);
            });
    }
}
