package io.casehub.yaml.step.handler;

import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;

import java.util.Map;

public class AgentSessionCloseHandler implements InvokeHandler {

    private final SessionPipeline pipeline;

    public AgentSessionCloseHandler(SessionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.SessionClose;
    }

    @Override
    public Action create(Declaration declaration, InvokeBinding binding) {
        var close = (InvokeBinding.SessionClose) binding;
        return (params, services) -> {
            try {
                pipeline.releaseSession(close.session());
                return Result.of(Map.of("session", close.session(), "closed", true), Map.of());
            } catch (Exception e) {
                return Result.failed("Failed to close session: " + e.getMessage());
            }
        };
    }
}
