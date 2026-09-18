package io.casehub.platform.simulation.event.quarkus;

import io.casehub.platform.simulation.event.SimulatedEventEmitter;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EventSimulationScheduler {

    private static final Logger LOG = Logger.getLogger(EventSimulationScheduler.class);

    @Inject
    SimulatedEventEmitter emitter;

    @Scheduled(every = "${casehub.simulation.event.interval:OFF}",
               identity = "event-simulation-tick")
    void tick() {
        var result = emitter.tick();
        if (result.emittedCount() > 0) {
            LOG.debugf("Event simulation tick: emitted %d event(s)", result.emittedCount());
        }
        if (result.hasFailures()) {
            result.failures().forEach(f ->
                    LOG.warnf(f.cause(), "Event simulation emission failed for %s",
                            f.qualifiedName()));
        }
    }
}
