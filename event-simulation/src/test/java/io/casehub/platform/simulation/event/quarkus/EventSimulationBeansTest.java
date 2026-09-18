package io.casehub.platform.simulation.event.quarkus;

import io.casehub.platform.simulation.event.EventSequenceRunner;
import io.casehub.platform.simulation.event.SimulatedEventEmitter;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class EventSimulationBeansTest {

    @Inject
    SimulatedEventEmitter emitter;

    @Inject
    EventSequenceRunner sequenceRunner;

    @Test
    void emitterIsProduced() {
        assertThat(emitter).isNotNull();
    }

    @Test
    void sequenceRunnerIsProduced() {
        assertThat(sequenceRunner).isNotNull();
    }

    @Test
    void tickWithNoStrategyEmitsNothing() {
        var result = emitter.tick();
        assertThat(result.emittedCount()).isZero();
        assertThat(result.hasFailures()).isFalse();
    }
}
