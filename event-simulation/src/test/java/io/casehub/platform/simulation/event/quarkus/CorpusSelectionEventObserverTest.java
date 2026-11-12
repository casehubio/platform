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

    @Test
    void conditionMatchingEventFiresTrigger() throws Exception {
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");

        CloudEvent event = CloudEventBuilder.v1()
                                            .withType("test.conditional")
                                            .withId("test-cond-1")
                                            .withSource(URI.create("//test"))
                                            .withData("application/json", "{\"level\": 10}".getBytes())
                                            .build();
        observer.onCloudEvent(event);

        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("active");
    }

    @Test
    void conditionNonMatchingEventDoesNotFireTrigger() throws Exception {
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");

        CloudEvent event = CloudEventBuilder.v1()
                                            .withType("test.conditional")
                                            .withId("test-cond-2")
                                            .withSource(URI.create("//test"))
                                            .withData("application/json", "{\"level\": 3}".getBytes())
                                            .build();
        observer.onCloudEvent(event);

        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");
    }

    @Test
    void overlayOverridesEventTypeAndPopRestoresBaseBinding() {
        var config = triggerConfig("""
                triggers:
                  activate:
                    event-type: profile.activate
                """);
        var overlay = runtime.pushOverlay(config);
        try {
            observer.onCloudEvent(event("test.activate", null));
            assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");
            observer.onCloudEvent(event("profile.activate", null));
            assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("active");
            runtime.resetSelector("test-spi.invoke");
        } finally {
            runtime.popOverlay(overlay);
        }
        observer.onCloudEvent(event("profile.activate", null));
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");
        observer.onCloudEvent(event("test.activate", null));
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("active");
    }

    @Test
    void changedConditionIsRecompiledAndPopRestoresBaseCondition() {
        var overlay = runtime.pushOverlay(triggerConfig("""
                triggers:
                  conditional-activate:
                    event-type: test.conditional
                    condition: "level > 20"
                """));
        try {
            observer.onCloudEvent(event("test.conditional", "{\"level\": 10}"));
            assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("idle");
            observer.onCloudEvent(event("test.conditional", "{\"level\": 30}"));
            assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("active");
            runtime.resetSelector("test-spi.invoke");
        } finally {
            runtime.popOverlay(overlay);
        }
        observer.onCloudEvent(event("test.conditional", "{\"level\": 10}"));
        assertThat(runtime.stateMachineStates().get("test-spi.invoke")).isEqualTo("active");
    }

    private static io.casehub.platform.simulation.config.YamlSimulationConfig triggerConfig(String yaml) {
        return new io.casehub.platform.simulation.config.YamlSimulationConfig(
                new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "test-tenant");
    }

    private static CloudEvent event(String type, String json) {
        var builder = CloudEventBuilder.v1().withType(type).withId(java.util.UUID.randomUUID().toString())
                .withSource(URI.create("//test"));
        if (json != null) {
            builder.withData("application/json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    // Keep trigger dispatch tests independent of MVEL's dynamic Java compiler.
    @jakarta.enterprise.context.ApplicationScoped
    @jakarta.enterprise.inject.Alternative
    @jakarta.annotation.Priority(1)
    public static class TriggerExpressionRegistry extends io.casehub.platform.expression.DefaultExpressionEngineRegistry {
        public TriggerExpressionRegistry() {
            super(java.util.List.of(new io.casehub.platform.expression.JexlExpressionEngine()));
            registerDefault(io.casehub.platform.api.expression.ExpressionContext.CONDITION, "jexl");
        }
    }

}
