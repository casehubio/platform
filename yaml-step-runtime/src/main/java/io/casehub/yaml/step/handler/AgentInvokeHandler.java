package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.spi.AgentDescriptorRegistrar;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
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
    public Action create(Declaration declaration, InvokeBinding binding) {
        InvokeBinding.Agent agent = (InvokeBinding.Agent) binding;
        return (params, services) -> executeAgent(agent, declaration, params);
    }

    @SuppressWarnings("unchecked")
    private Result executeAgent(InvokeBinding.Agent agent, Declaration declaration,
                                Map<String, Object> params) {
        if (agentProvider == null) {
            return Result.failed("AgentProvider not available");
        }

        try {
            AgentDescriptor descriptor = agent.descriptor() != null
                    ? resolveDescriptor(agent.descriptor()) : null;

            String systemPrompt = agent.system() != null
                    ? agent.system() : buildSystemPrompt(descriptor);
            String userPrompt = agent.user() != null
                    ? agent.user() : "Execute with the following inputs: "
                                     + objectMapper.writeValueAsString(params);

            String model = agent.model();
            if (model == null && descriptor != null && descriptor.modelFamily() != null) {
                model = descriptor.modelFamily();
            }

            Duration timeout = io.casehub.yaml.core.orchestration.DurationParser.parseOrNull(agent.timeout());

            io.casehub.platform.api.model.ModelQuery modelQuery = null;
            if (agent.modelQuery() != null) {
                modelQuery = buildModelQuery(agent.modelQuery());
            }

            AgentSessionConfig config = new AgentSessionConfig(
                    systemPrompt, userPrompt, List.of(), timeout, null, model, modelQuery);

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
                return Result.of(parsed, metadata);
            }

            return Result.of(Map.of("response", responseText), metadata);

        } catch (io.casehub.platform.agent.AgentTimeoutException e) {
            return Result.failed("Agent timed out: " + e.getMessage());
        } catch (io.casehub.platform.agent.AgentProcessException e) {
            return Result.failed("Agent process error: " + e.getMessage());
        } catch (Exception e) {
            return Result.failed("Agent invocation failed: " + e.getMessage());
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

    @SuppressWarnings("unchecked")
    private io.casehub.platform.api.model.ModelQuery buildModelQuery(Map<String, Object> map) {
        var builder = io.casehub.platform.api.model.ModelQuery.builder();
        if (map.containsKey("vendor")) {builder.vendor((String) map.get("vendor"));}
        if (map.containsKey("family")) {builder.family((String) map.get("family"));}
        if (map.containsKey("tier")) {
            builder.tier(io.casehub.platform.api.model.ModelTier.valueOf(
                    ((String) map.get("tier")).toUpperCase()));
        }
        if (map.containsKey("capabilities")) {
            builder.requiredCapabilities(new java.util.LinkedHashSet<>((List<String>) map.get("capabilities")));
        }
        if (map.containsKey("locality")) {
            builder.locality(io.casehub.platform.api.model.ModelLocality.valueOf(
                    ((String) map.get("locality")).toUpperCase()));
        }
        if (map.containsKey("max-cost-tier")) {
            builder.maxCostTier(io.casehub.platform.api.model.CostTier.valueOf(
                    ((String) map.get("max-cost-tier")).toUpperCase()));
        }
        if (map.containsKey("min-context-window")) {
            builder.minContextWindow(((Number) map.get("min-context-window")).intValue());
        }
        if (map.containsKey("prefer-vendor")) {builder.preferVendor((String) map.get("prefer-vendor"));}
        if (map.containsKey("backend")) {builder.backendKey((String) map.get("backend"));}
        return builder.build();
    }


}
