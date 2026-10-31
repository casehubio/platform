package io.casehub.yaml.step.handler;

import io.casehub.platform.agent.session.ManagedSession;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;

import java.util.Map;

public class AgentSessionClearHandler implements InvokeHandler {

    private final SessionPipeline pipeline;

    public AgentSessionClearHandler(SessionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.SessionClear;
    }

    @Override
    public Action create(Declaration declaration, InvokeBinding binding) {
        var clear = (InvokeBinding.SessionClear) binding;
        return (params, services) -> {
            try {
                var session = pipeline.sessionFor(clear.session());
                if (session == null) {
                    return Result.failed("Session not found: " + clear.session());
                }
                session.clear();
                return Result.of(Map.of("session", clear.session(), "cleared", true), Map.of());
            } catch (Exception e) {
                return Result.failed("Failed to clear session: " + e.getMessage());
            }
        };
    }
}
