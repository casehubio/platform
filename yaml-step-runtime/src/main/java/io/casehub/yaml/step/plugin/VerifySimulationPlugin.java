package io.casehub.yaml.step.plugin;

import io.casehub.platform.simulation.SimulationOverlay;
import io.casehub.platform.simulation.SimulationVerifier;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;

import java.util.List;
import java.util.Map;

public final class VerifySimulationPlugin implements Action {

    public static Definition definition() {
        return Definition.of("verify-simulation")
                .description("Assertions on simulation invocation journal")
                .execute(new VerifySimulationPlugin())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        SimulationOverlay overlay;
        try {
            overlay = services.lookup(SimulationOverlay.class);
        } catch (IllegalArgumentException e) {
            return Result.failed("verify-simulation requires an active simulation overlay");
        }

        var verifier = SimulationVerifier.on(overlay);

        try {
            String method = (String) params.get("method");

            if (params.containsKey("called")) {
                int expected = ((Number) params.get("called")).intValue();
                verifier.method(method).wasCalled(expected);
            }

            if (params.containsKey("calledAtLeast")) {
                int min = ((Number) params.get("calledAtLeast")).intValue();
                verifier.method(method).wasCalledAtLeast(min);
            }

            if (params.containsKey("calledAtMost")) {
                int max = ((Number) params.get("calledAtMost")).intValue();
                verifier.method(method).wasCalledAtMost(max);
            }

            if (Boolean.TRUE.equals(params.get("neverCalled"))) {
                verifier.method(method).wasNeverCalled();
            }

            if (params.containsKey("inOrder")) {
                var methods = (List<String>) params.get("inOrder");
                verifier.inOrder(methods.toArray(new String[0]));
            }

            if (Boolean.TRUE.equals(params.get("exhaustive"))) {
                if (method != null) {
                    verifier.method(method).allSimulated();
                } else {
                    verifier.noUnverifiedCalls();
                }
            }

            return Result.of(Map.of("verified", true));
        } catch (AssertionError e) {
            return Result.failed("Simulation verification failed: " + e.getMessage());
        }
    }
}
