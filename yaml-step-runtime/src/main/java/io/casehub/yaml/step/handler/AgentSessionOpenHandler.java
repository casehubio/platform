package io.casehub.yaml.step.handler;

import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ClearingPolicy;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;

import java.util.Map;

public class AgentSessionOpenHandler implements InvokeHandler {

    private final SessionPipeline pipeline;

    public AgentSessionOpenHandler(SessionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.SessionOpen;
    }

    @Override
    public Action create(Declaration declaration, InvokeBinding binding) {
        var open = (InvokeBinding.SessionOpen) binding;
        return (params, services) -> {
            try {
                var init = AgentSessionInit.of(
                        open.system() != null ? open.system() : "You are a helpful assistant.");
                if (open.model() != null) {
                    init = init.withModel(open.model());
                }

                pipeline.openSession(open.session(), init, ClearingPolicy.MANUAL);
                return Result.of(Map.of("session", open.session()), Map.of());
            } catch (Exception e) {
                return Result.failed("Failed to open session: " + e.getMessage());
            }
        };
    }
}
