package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.ResponseFormatType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JsonSchemaOutputGuardrail implements OutputGuardrail {

    @Override
    public GuardrailResult validate(String response, AgentSessionConfig originalConfig) {
        if (originalConfig.responseFormat() == null
                || originalConfig.responseFormat().type() == ResponseFormatType.TEXT) {
            return new GuardrailResult.Pass();
        }
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
            return new GuardrailResult.Pass();
        } catch (Exception e) {
            return new GuardrailResult.Fail("Response is not valid JSON: " + e.getMessage());
        }
    }
}
