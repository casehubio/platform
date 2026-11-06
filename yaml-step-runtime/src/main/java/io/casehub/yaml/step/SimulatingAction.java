package io.casehub.yaml.step;

import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.SimulationStrategy;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;

import java.util.Map;
import java.util.Optional;

public class SimulatingAction implements Action {

    private final Action delegate;
    private final String qualifiedName;
    private final String lookupKey;
    private final SimulationRuntime runtime;

    public SimulatingAction(Action delegate, InvokeBinding binding, SimulationRuntime runtime) {
        this.delegate = delegate;
        this.runtime = runtime;
        this.qualifiedName = qualifiedNameFor(binding);
        this.lookupKey = keyFor(binding);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        if (runtime.hasActiveOverlay() && lookupKey != null) {
            Optional<SimulationStrategy<Object, Object>> strategy = runtime.strategyFor(qualifiedName);
            if (strategy.isPresent() && strategy.get().canResolve(lookupKey)) {
                Object simulated = strategy.get().resolve(lookupKey);
                Map<String, Object> output = simulated instanceof Map
                        ? (Map<String, Object>) simulated
                        : Map.of("response", simulated);
                return Result.of(output, Map.of("simulated", true));
            }
        }
        return delegate.execute(params, services);
    }

    private static String qualifiedNameFor(InvokeBinding binding) {
        return switch (binding) {
            case InvokeBinding.Rest ignored -> "rest-request";
            case InvokeBinding.Graphql ignored -> "graphql-exec";
            case InvokeBinding.Mcp ignored -> "mcp-call";
            case InvokeBinding.SessionClose ignored -> "agent-session-close";
            case InvokeBinding.SessionClear ignored -> "agent-session-clear";
            case InvokeBinding.SessionOpen ignored -> "agent-session-open";
            case InvokeBinding.SessionQuery ignored -> "agent-query";
            case InvokeBinding.Agent ignored -> "agent";
            case InvokeBinding.Script ignored -> "script";
            case InvokeBinding.Process ignored -> "process";
            case InvokeBinding.Aria ignored -> "aria";
        };
    }

    private static String keyFor(InvokeBinding binding) {
        return switch (binding) {
            case InvokeBinding.Rest r -> r.url();
            case InvokeBinding.Graphql g -> g.query();
            case InvokeBinding.Mcp m -> m.tool();
            case InvokeBinding.SessionClose c -> c.session();
            case InvokeBinding.SessionClear c -> c.session();
            default -> null;
        };
    }
}
