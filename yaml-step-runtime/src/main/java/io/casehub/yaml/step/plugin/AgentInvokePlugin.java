package io.casehub.yaml.step.plugin;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;

import java.time.Duration;
import java.util.Map;

public final class AgentInvokePlugin implements Action {

    public static Definition definition() {
        return Definition.of("agent-invoke")
                .description("Single-shot agent invocation (open → query → close)")
                .execute(new AgentInvokePlugin())
                .build();
    }

    @Override
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        AgentProvider provider;
        try {
            provider = services.lookup(AgentProvider.class);
        } catch (IllegalArgumentException e) {
            return Result.failed("No AgentProvider registered — agent-invoke requires an agent provider");
        }

        String prompt = (String) params.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return Result.failed("agent-invoke requires a 'prompt' parameter");
        }

        try {
            long start = System.nanoTime();
            var invResult = provider.invokeBlocking(null, prompt, Duration.ofSeconds(30));
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            return Result.of(Map.of(
                    "text", invResult.text(),
                    "tokens", invResult.outputTokens(),
                    "duration", durationMs
            ));
        } catch (Exception e) {
            return Result.failed("Agent invocation failed: " + e.getMessage());
        }
    }
}
