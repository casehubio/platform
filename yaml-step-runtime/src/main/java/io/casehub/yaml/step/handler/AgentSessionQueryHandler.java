package io.casehub.yaml.step.handler;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentSessionQueryHandler implements InvokeHandler {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final SessionPipeline pipeline;

    public AgentSessionQueryHandler(SessionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.SessionQuery;
    }

    @Override
    public Action create(Declaration declaration, InvokeBinding binding) {
        var query = (InvokeBinding.SessionQuery) binding;
        return (params, services) -> {
            try {
                var responseText = new StringBuilder();
                var metadata = new LinkedHashMap<String, Object>();

                if (query.priority()) {
                    pipeline.executeSinglePriority(query.session(), session -> {
                        session.query(query.user())
                                .subscribe().asStream()
                                .forEach(event -> collectEvent(event, responseText, metadata));
                        return null;
                    }, DEFAULT_TIMEOUT);
                } else {
                    pipeline.executeSingle(query.session(), session -> {
                        session.query(query.user())
                                .subscribe().asStream()
                                .forEach(event -> collectEvent(event, responseText, metadata));
                        return null;
                    }, DEFAULT_TIMEOUT);
                }

                var result = new LinkedHashMap<String, Object>();
                result.put("response", responseText.toString());
                result.putAll(metadata);
                return Result.of(result, Map.of());
            } catch (Exception e) {
                return Result.failed("Session query failed: " + e.getMessage());
            }
        };
    }

    private void collectEvent(AgentEvent event, StringBuilder text, Map<String, Object> metadata) {
        if (event instanceof AgentEvent.TextDelta td) {
            text.append(td.text());
        } else if (event instanceof AgentEvent.InvocationComplete ic) {
            metadata.put("inputTokens", ic.inputTokens());
            metadata.put("outputTokens", ic.outputTokens());
            metadata.put("durationMs", ic.durationMs());
        }
    }
}
