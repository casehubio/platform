package io.casehub.platform.simulation.event.quarkus;

import io.casehub.platform.simulation.SimulationRuntime;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CorpusSelectionEventObserverTest {

    @Inject
    SimulationRuntime runtime;

    @Inject
    CorpusSelectionEventObserver observer;

    @BeforeEach
    void resetState() {
        runtime.advanceStateMachines("__reset__");
        var sms = runtime.stateMachineSelector("test-spi.invoke");
        if (sms.isPresent()) {
            sms.get().reset();
        }
    }

    @Test
    void cloudEventTriggersStateMachineTransition() {
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");

        CloudEvent event = CloudEventBuilder.v1()
                .withType("test.activate")
                .withId("test-1")
                .withSource(URI.create("//test"))
                .build();
        observer.onCloudEvent(event);

        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("active");
    }

    @Test
    void unmatchedEventTypeDoesNotTransition() {
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");

        CloudEvent event = CloudEventBuilder.v1()
                .withType("unrelated.event")
                .withId("test-2")
                .withSource(URI.create("//test"))
                .build();
        observer.onCloudEvent(event);

        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");
    }

    @Test
    void sequentialTriggersAdvanceThroughStates() {
        CloudEvent activate = CloudEventBuilder.v1()
                .withType("test.activate")
                .withId("test-3a")
                .withSource(URI.create("//test"))
                .build();
        observer.onCloudEvent(activate);
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("active");

        CloudEvent complete = CloudEventBuilder.v1()
                .withType("test.complete")
                .withId("test-3b")
                .withSource(URI.create("//test"))
                .build();
        observer.onCloudEvent(complete);
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("done");
    }
}
