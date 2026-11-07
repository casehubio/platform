package io.casehub.yaml.test;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ClearingPolicy;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class AgentSessionPlugins {

    private AgentSessionPlugins() {}

    public static List<Definition> definitions(SessionPipeline pipeline) {
        return List.of(
                openDefinition(pipeline),
                queryDefinition("agent-session-query", pipeline),
                queryDefinition("agent-query", pipeline),
                clearDefinition(pipeline),
                closeDefinition(pipeline));
    }

    private static Definition openDefinition(SessionPipeline pipeline) {
        return Definition.of("agent-session-open")
                .description("Opens an agent session")
                .execute((params, services) -> {
                    String sessionId = resolveSessionId(params);
                    String systemPrompt = params.containsKey("system")
                            ? String.valueOf(params.get("system"))
                            : params.containsKey("system-prompt")
                                    ? String.valueOf(params.get("system-prompt")) : "";
                    pipeline.openSession(sessionId,
                            AgentSessionInit.of(systemPrompt), ClearingPolicy.MANUAL);
                    return Result.of(Map.of("sessionId", sessionId));
                })
                .build();
    }

    private static Definition queryDefinition(String name, SessionPipeline pipeline) {
        return Definition.of(name)
                .description("Queries an agent session")
                .execute((params, services) -> {
                    String sessionId = resolveSessionId(params);
                    String prompt = params.containsKey("user")
                            ? String.valueOf(params.get("user"))
                            : params.containsKey("prompt")
                                    ? String.valueOf(params.get("prompt")) : "";
                    var responseBuilder = new StringBuilder();
                    pipeline.executeSingle(sessionId, session -> {
                        session.query(prompt).subscribe().asStream().forEach(event -> {
                            if (event instanceof AgentEvent.TextDelta td) {
                                responseBuilder.append(td.text());
                            }
                        });
                        return null;
                    }, Duration.ofSeconds(10));
                    var output = new java.util.LinkedHashMap<String, Object>();
                    output.put("response", responseBuilder.toString());
                    if (params.containsKey("output")) {
                        output.put(String.valueOf(params.get("output")), responseBuilder.toString());
                    }
                    return Result.of(output);
                })
                .build();
    }

    private static Definition clearDefinition(SessionPipeline pipeline) {
        return Definition.of("agent-session-clear")
                .description("Clears an agent session context")
                .execute((params, services) -> {
                    String sessionId = resolveSessionId(params);
                    pipeline.executeSingle(sessionId, session -> {
                        session.clear();
                        return null;
                    }, Duration.ofSeconds(5));
                    return Result.of(Map.of());
                })
                .build();
    }

    private static Definition closeDefinition(SessionPipeline pipeline) {
        return Definition.of("agent-session-close")
                .description("Closes an agent session")
                .execute((params, services) -> {
                    String sessionId = resolveSessionId(params);
                    pipeline.releaseSession(sessionId);
                    return Result.of(Map.of());
                })
                .build();
    }

    private static String resolveSessionId(Map<String, Object> params) {
        if (params.containsKey("session")) return String.valueOf(params.get("session"));
        if (params.containsKey("_subject")) return String.valueOf(params.get("_subject"));
        return "default";
    }
}
