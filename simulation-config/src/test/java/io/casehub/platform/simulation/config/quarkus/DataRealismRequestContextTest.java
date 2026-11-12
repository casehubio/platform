package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.DataRealism;
import io.casehub.platform.simulation.DataRealismContext;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DataRealismRequestContextTest {
    @Inject
    DataRealismContext observations;

    @Inject
    Consumer consumer;

    @Test
    void callersShareCurrentObservationWithinRequestButNotAcrossRequests() {
        var request = Arc.container().requestContext();
        var previous = request.isActive() ? request.getState() : null;
        request.deactivate();
        try {
            request.activate();
            assertThat(consumer.current()).isNull();
            observations.set("contacts.find", DataRealism.RECORDED_REAL);
            assertThat(consumer.current()).isEqualTo(DataRealism.RECORDED_REAL);
            assertThat(consumer.qualifiedName()).isEqualTo("contacts.find");

            observations.set("contacts.live", null);
            assertThat(consumer.current()).isNull();
            assertThat(consumer.qualifiedName()).isEqualTo("contacts.live");
            request.terminate();

            request.activate();
            assertThat(observations.current()).isNull();
            assertThat(consumer.current()).isNull();
            observations.set("contacts.list", DataRealism.DOMAIN_PLAUSIBLE);
            assertThat(consumer.current()).isEqualTo(DataRealism.DOMAIN_PLAUSIBLE);
        } finally {
            request.terminate();
            if (previous != null) {
                request.activate(previous);
            }
        }
    }

    @ApplicationScoped
    public static class Consumer {
        @Inject
        DataRealismContext observations;

        DataRealism current() {
            return observations.dataRealism();
        }

        String qualifiedName() {
            return observations.current().qualifiedName();
        }
    }
}
