package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MaxTokenInputGuardrail implements InputGuardrail {

    @ConfigProperty(name = "casehub.guardrail.max-input-tokens", defaultValue = "100000")
    int maxTokens;

    @Override
    public GuardrailResult validate(AgentSessionConfig config) {
        int charCount = config.userPrompt().length()
            + (config.systemPrompt() != null ? config.systemPrompt().length() : 0);
        int estimatedTokens = charCount / 4;
        if (estimatedTokens > maxTokens) {
            return new GuardrailResult.Fail(
                "Input exceeds maximum token limit: estimated " + estimatedTokens + " > " + maxTokens);
        }
        return new GuardrailResult.Pass();
    }
}
