package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.InvokeHandler;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentInvokeHandler implements InvokeHandler {

    private final ObjectMapper objectMapper;

    public AgentInvokeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Agent;
    }

    @Override
    public StepAction create(StepDefinition definition, InvokeBinding binding) {
        InvokeBinding.Agent agent = (InvokeBinding.Agent) binding;
        return (params, services) -> executeAgent(agent, definition, params, services);
    }

    @SuppressWarnings("unchecked")
    private StepResult executeAgent(InvokeBinding.Agent agent, StepDefinition definition,
                                     Map<String, Object> params,
                                     io.casehub.yaml.plugin.api.ServiceRegistry services) {
        try {
            String userPrompt = "Execute with the following inputs: "
                    + objectMapper.writeValueAsString(params);

            if (services == null) {
                return StepResult.failed("AgentProvider not available — no ServiceRegistry");
            }

            Object agentProvider = services.lookup(Object.class);
            if (agentProvider == null) {
                return StepResult.failed("AgentProvider not registered in ServiceRegistry");
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("descriptor", agent.descriptor());
            if (agent.model() != null) metadata.put("model", agent.model());

            return StepResult.of(Map.of("response", "Agent invocation placeholder"), metadata);
        } catch (Exception e) {
            return StepResult.failed("Agent invocation failed: " + e.getMessage());
        }
    }
}
