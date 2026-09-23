package io.casehub.platform.simulation.event.quarkus;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.simulation.DriverResult;
import io.casehub.platform.simulation.TemporalDriverFactory;
import io.casehub.platform.simulation.TemporalProfile;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.TemporalSimulationDriver;
import io.casehub.platform.simulation.TimedEntry;
import io.casehub.platform.simulation.TimedSequence;
import io.casehub.platform.simulation.config.DurationParser;
import io.casehub.platform.simulation.config.TemporalProfileRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@McpDomain("temporal-drivers")
public class TemporalDriverService {

    private final TemporalDriverFactory<Map<String, Object>> driverFactory;
    private final TemporalProfileRegistry profileRegistry;
    private final SimulationRuntime simulationRuntime;

    private final ConcurrentHashMap<String, ActiveDriver> activeDrivers = new ConcurrentHashMap<>();

    record ActiveDriver(
            String name,
            String profileName,
            TemporalSimulationDriver<Map<String, Object>> driver) {}

    @Inject
    public TemporalDriverService(
            TemporalDriverFactory<Map<String, Object>> driverFactory,
            TemporalProfileRegistry profileRegistry,
            SimulationRuntime simulationRuntime) {
        this.driverFactory = driverFactory;
        this.profileRegistry = profileRegistry;
        this.simulationRuntime = simulationRuntime;
    }

    @PlatformMutation("Start a temporal simulation driver")
    public TemporalDriverStatus start(TemporalDriverStartRequest request) {
        request.validate();
        String name = request.effectiveName();

        activeDrivers.compute(name, (key, existing) -> {
            if (existing != null) {
                var state = existing.driver().lifecycle().currentState();
                if (state == TemporalSimulationDriver.State.RUNNING
                        || state == TemporalSimulationDriver.State.PAUSED) {
                    throw new IllegalStateException(
                            "Driver '" + key + "' is " + state + ". Stop it first.");
                }
            }

            TemporalProfile<Map<String, Object>> profile = resolveProfile(request);
            var driver = driverFactory.create();
            driver.start(profile);
            return new ActiveDriver(key, request.profileName(), driver);
        });

        return buildStatus(name, activeDrivers.get(name));
    }

    @PlatformMutation("Stop a temporal simulation driver")
    @RestMethod(HttpMethod.DELETE)
    public void stop(@PathParam String name) {
        var active = activeDrivers.remove(name);
        if (active == null) throw new NotFoundException("Driver not found: " + name);
        active.driver().stop();
    }

    @PlatformMutation("Pause a temporal simulation driver")
    @RestMethod(HttpMethod.PUT)
    public void pause(@PathParam String name) {
        var active = requireDriver(name);
        active.driver().pause();
    }

    @PlatformMutation("Resume a paused temporal simulation driver")
    @RestMethod(HttpMethod.PUT)
    public void resume(@PathParam String name) {
        var active = requireDriver(name);
        active.driver().resume();
    }

    @PlatformMutation("Change speed of a temporal simulation driver")
    @RestMethod(HttpMethod.PUT)
    public void setSpeed(TemporalDriverSpeedRequest request) {
        var active = requireDriver(request.name());
        active.driver().setSpeed(request.speed());
    }

    @PlatformQuery("Get status of a temporal simulation driver")
    public TemporalDriverStatus status(@PathParam String name) {
        var active = requireDriver(name);
        return buildStatus(name, active);
    }

    @PlatformQuery("List all active temporal simulation drivers")
    public List<TemporalDriverStatus> list() {
        return activeDrivers.entrySet().stream()
                .map(e -> buildStatus(e.getKey(), e.getValue()))
                .toList();
    }

    @PlatformMutation("Set the global speed multiplier for all temporal drivers")
    public void setGlobalSpeed(double speed) {
        simulationRuntime.setGlobalSpeed(speed);
    }

    @PlatformQuery("Get the current global speed multiplier")
    public double globalSpeed() {
        return simulationRuntime.globalSpeed();
    }

    @PlatformMutation("Reset a driver's speed override, reverting to global composition")
    public void resetDriverSpeed(@PathParam String name) {
        requireDriver(name).driver().resetSpeed();
    }


    private ActiveDriver requireDriver(String name) {
        var active = activeDrivers.get(name);
        if (active == null) throw new NotFoundException("Driver not found: " + name);
        return active;
    }

    private TemporalProfile<Map<String, Object>> resolveProfile(
            TemporalDriverStartRequest request) {
        if (request.profileName() != null && !request.profileName().isBlank()) {
            var profile = profileRegistry.resolve(request.profileName())
                    .orElseThrow(() -> new NotFoundException(
                            "Profile not found: " + request.profileName()));
            double speed = request.speed() != null ? request.speed() : profile.speed();
            boolean loop = request.loop() != null ? request.loop() : profile.loop();
            return new TemporalProfile<>(profile.name(), profile.qualifiedName(),
                    profile.tenancyId(), profile.sequence(), loop, speed);
        }

        List<TimedEntry<Map<String, Object>>> entries = request.events() == null
                ? List.of()
                : request.events().stream()
                .map(e -> new TimedEntry<>(
                        e.payload(),
                        DurationParser.parse(e.delay()),
                        e.label()))
                .toList();

        return new TemporalProfile<>(
                request.effectiveName(),
                request.qualifiedName(),
                request.tenancyId(),
                new TimedSequence<>(entries),
                request.loop() != null && request.loop(),
                request.speed() != null ? request.speed() : 1.0);
    }

    private TemporalDriverStatus buildStatus(String name, ActiveDriver active) {
        var driver = active.driver();
        DriverResult result = driver.lastResult();
        return new TemporalDriverStatus(
                name,
                active.profileName(),
                driver.lifecycle().currentState().name(),
                driver.speed(),
                result != null ? result.emittedCount() : 0,
                result != null ? result.failureCount() : 0,
                result != null ? result.loopIterations() : 0,
                result != null && result.hasFailures());
    }
}
