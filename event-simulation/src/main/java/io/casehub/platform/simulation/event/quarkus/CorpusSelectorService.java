package io.casehub.platform.simulation.event.quarkus;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.simulation.SimulationRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
@McpDomain("corpus-selectors")
public class CorpusSelectorService {

    private final SimulationRuntime simulationRuntime;

    @Inject
    public CorpusSelectorService(SimulationRuntime simulationRuntime) {
        this.simulationRuntime = simulationRuntime;
    }

    @PlatformQuery("List all active corpus selectors with type and current phase")
    public List<CorpusSelectorStatus> list() {
        return simulationRuntime.allSelectorInfo().entrySet().stream()
                .map(e -> new CorpusSelectorStatus(e.getKey(), e.getValue().type(), e.getValue().activePhase()))
                .toList();
    }

    @PlatformQuery("Get status of a specific corpus selector")
    public CorpusSelectorStatus status(@PathParam String qualifiedName) {
        var info = simulationRuntime.allSelectorInfo().get(qualifiedName);
        if (info == null) {
            throw new NotFoundException("Selector not found: " + qualifiedName);
        }
        return new CorpusSelectorStatus(qualifiedName, info.type(), info.activePhase());
    }

    @PlatformMutation("Advance a state machine corpus selector by trigger")
    public CorpusSelectorStatus advance(CorpusSelectorAdvanceRequest request) {
        request.validate();
        simulationRuntime.advanceStateMachine(request.qualifiedName(), request.trigger());
        return status(request.qualifiedName());
    }

    @PlatformMutation("Reset a corpus selector to its initial state")
    @RestMethod(HttpMethod.PUT)
    public CorpusSelectorStatus reset(@PathParam String qualifiedName) {
        var info = simulationRuntime.allSelectorInfo().get(qualifiedName);
        if (info == null) {
            throw new NotFoundException("Selector not found: " + qualifiedName);
        }
        simulationRuntime.resetSelector(qualifiedName);
        return status(qualifiedName);
    }
}
