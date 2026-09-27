package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.spi.AgentDescriptorRegistrar;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.InvokeHandler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentInvokeHandler implements InvokeHandler {

    private final ObjectMapper                   objectMapper;
    private final AgentProvider                  agentProvider;
    private final List<AgentDescriptorRegistrar> registrars;
    private final Duration                       defaultTimeout;

    public AgentInvokeHandler(ObjectMapper objectMapper,
                              AgentProvider agentProvider,
                              List<AgentDescriptorRegistrar> registrars) {
        this(objectMapper, agentProvider, registrars, Duration.ofSeconds(60));
    }

    public AgentInvokeHandler(ObjectMapper objectMapper,
                              AgentProvider agentProvider,
                              List<AgentDescriptorRegistrar> registrars,
                              Duration defaultTimeout) {
        this.objectMapper   = objectMapper;
        this.agentProvider  = agentProvider;
        this.registrars     = registrars != null ? registrars : List.of();
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(60);
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Agent;
    }

    @Override
    public StepAction create(StepDefinition definition, InvokeBinding binding) {
        InvokeBinding.Agent agent = (InvokeBinding.Agent) binding;
        return (params, services) -> executeAgent(agent, definition, params);
    }

    @SuppressWarnings("unchecked")
    private StepResult executeAgent(InvokeBinding.Agent agent, StepDefinition definition,
                                    Map<String, Object> params) {
        if (agentProvider == null) {
            return StepResult.failed("AgentProvider not available");
        }

        try {
            AgentDescriptor descriptor = resolveDescriptor(agent.descriptor());

            String systemPrompt = buildSystemPrompt(descriptor);
            String userPrompt = "Execute with the following inputs: "
                                + objectMapper.writeValueAsString(params);

            String model = agent.model();
            if (model == null && descriptor != null && descriptor.modelFamily() != null) {
                model = descriptor.modelFamily();
            }

            Duration timeout = io.casehub.yaml.core.orchestration.DurationParser.parseOrNull(agent.timeout());

            AgentSessionConfig config = new AgentSessionConfig(
                    systemPrompt, userPrompt, List.of(), timeout, null, model);

            List<AgentEvent> events = agentProvider.invoke(config)
                                                   .collect().asList()
                                                   .await().atMost(timeout != null ? timeout : defaultTimeout);

            StringBuilder       text     = new StringBuilder();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("descriptor", agent.descriptor());

            for (AgentEvent event : events) {
                if (event instanceof AgentEvent.TextDelta td) {
                    text.append(td.text());
                } else if (event instanceof AgentEvent.InvocationComplete ic) {
                    metadata.put("inputTokens", ic.inputTokens());
                    metadata.put("outputTokens", ic.outputTokens());
                    metadata.put("durationMs", ic.durationMs());
                    if (ic.totalCostUsd() != null) {
                        metadata.put("costUsd", ic.totalCostUsd());
                    }
                }
            }

            String responseText = text.toString();

            if (agent.structuredOutput()) {
                Map<String, Object> parsed = objectMapper.readValue(responseText, LinkedHashMap.class);
                return StepResult.of(parsed, metadata);
            }

            return StepResult.of(Map.of("response", responseText), metadata);

        } catch (io.casehub.platform.agent.AgentTimeoutException e) {
            return StepResult.failed("Agent timed out: " + e.getMessage());
        } catch (io.casehub.platform.agent.AgentProcessException e) {
            return StepResult.failed("Agent process error: " + e.getMessage());
        } catch (Exception e) {
            return StepResult.failed("Agent invocation failed: " + e.getMessage());
        }
    }

    private AgentDescriptor resolveDescriptor(String descriptorRef) {
        for (AgentDescriptorRegistrar registrar : registrars) {
            for (AgentDescriptor d : registrar.descriptors()) {
                if (descriptorRef.equals(d.agentId()) || descriptorRef.equals(d.name())) {
                    return d;
                }
            }
        }
        return null;
    }

    private String buildSystemPrompt(AgentDescriptor descriptor) {
        if (descriptor == null) {
            return "You are a helpful assistant.";
        }
        StringBuilder sb = new StringBuilder();
        if (descriptor.briefing() != null) {
            sb.append(descriptor.briefing());
        }
        if (!descriptor.goals().isEmpty()) {
            sb.append("\n\nGoals:\n");
            for (var goal : descriptor.goals()) {
                sb.append("- ").append(goal.name());
                if (goal.description() != null) {sb.append(": ").append(goal.description());}
                sb.append("\n");
            }
        }
        if (!descriptor.constraints().isEmpty()) {
            sb.append("\nConstraints:\n");
            for (var constraint : descriptor.constraints()) {
                sb.append("- ").append(constraint.name());
                if (constraint.description() != null) {sb.append(": ").append(constraint.description());}
                sb.append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "You are a helpful assistant.";
    }

}
