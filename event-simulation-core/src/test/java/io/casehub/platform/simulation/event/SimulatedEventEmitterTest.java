package io.casehub.platform.simulation.event;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedEventEmitterTest {

    private static final String QN = "event-emitter.workitem-completed";

    @Test
    void tickEmitsEventFromCorpus() {
        var corpus = new InMemorySimulationCorpus<EventTrigger, CloudEvent>();
        var config = stubConfig(Optional.of("sequential"), Optional.empty());
        var runtime = new SimulationRuntime(config, corpus);

        CloudEvent template = CloudEventBuilder.v1()
                .withId("template-id")
                .withType("io.casehub.work.workitem.completed")
                .withSource(URI.create("/simulation/event-emitter"))
                .withExtension("tenancyid", "default")
                .withData("application/json", "{\"workItemId\":\"WI-001\"}".getBytes())
                .build();

        corpus.seed(QN, List.of(new InvocationRecord<>(
                "default", "wic",
                new EventTrigger("io.casehub.work.workitem.completed", "default", Map.of()),
                template, Instant.now())));

        List<CloudEvent> emitted = new ArrayList<>();
        var emitter = new SimulatedEventEmitter(runtime, emitted::add,
                List.of(new EventSourceConfig(QN, "io.casehub.work.workitem.completed", "default")));

        EmissionResult result = emitter.tick();

        assertThat(result.emittedCount()).isEqualTo(1);
        assertThat(result.hasFailures()).isFalse();
        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).getType()).isEqualTo("io.casehub.work.workitem.completed");
    }

    @Test
    void tickStampsFreshIdAndTime() {
        var corpus = new InMemorySimulationCorpus<EventTrigger, CloudEvent>();
        var config = stubConfig(Optional.of("sequential"), Optional.empty());
        var runtime = new SimulationRuntime(config, corpus);

        CloudEvent template = CloudEventBuilder.v1()
                .withId("original-id")
                .withType("test.event")
                .withSource(URI.create("/test"))
                .withExtension("tenancyid", "t1")
                .build();

        corpus.seed(QN, List.of(new InvocationRecord<>(
                "t1", null,
                new EventTrigger("test.event", "t1", Map.of()),
                template, Instant.now())));

        List<CloudEvent> emitted = new ArrayList<>();
        var emitter = new SimulatedEventEmitter(runtime, emitted::add,
                List.of(new EventSourceConfig(QN, "test.event", "t1")));

        emitter.tick();
        emitter.tick();

        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(0).getId()).isNotEqualTo("original-id");
        assertThat(emitted.get(1).getId()).isNotEqualTo("original-id");
        assertThat(emitted.get(0).getId()).isNotEqualTo(emitted.get(1).getId());
        assertThat(emitted.get(0).getTime()).isNotNull();
        assertThat(emitted.get(1).getTime()).isNotNull();
    }

    @Test
    void tickSkipsSourcesWithNoStrategy() {
        var corpus = new InMemorySimulationCorpus<EventTrigger, CloudEvent>();
        var config = stubConfig(Optional.empty(), Optional.empty());
        var runtime = new SimulationRuntime(config, corpus);

        List<CloudEvent> emitted = new ArrayList<>();
        var emitter = new SimulatedEventEmitter(runtime, emitted::add,
                List.of(new EventSourceConfig(QN, "test.event", "t1")));

        EmissionResult result = emitter.tick();

        assertThat(result.emittedCount()).isZero();
        assertThat(result.hasFailures()).isFalse();
        assertThat(emitted).isEmpty();
    }

    @Test
    void tickSkipsSourcesWhenCannotResolve() {
        var corpus = new InMemorySimulationCorpus<EventTrigger, CloudEvent>();
        var config = stubConfig(Optional.of("key-lookup"), Optional.empty());
        var runtime = new SimulationRuntime(config, corpus);
        runtime.registerExtractor(QN, SimulatedEventEmitter.defaultKeyExtractor());

        List<CloudEvent> emitted = new ArrayList<>();
        var emitter = new SimulatedEventEmitter(runtime, emitted::add,
                List.of(new EventSourceConfig(QN, "missing.event", "t1")));

        EmissionResult result = emitter.tick();

        assertThat(result.emittedCount()).isZero();
        assertThat(emitted).isEmpty();
    }

    @Test
    void tickIsolatesErrorsPerSource() {
        var corpus = new InMemorySimulationCorpus<EventTrigger, CloudEvent>();
        var config = new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qn) {
                if (qn.equals("event-emitter.bad")) {
                    return Optional.of("key-lookup");
                }
                return Optional.of("sequential");
            }

            @Override
            public boolean captureEnabled(final String qn) {
                return false;
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qn) {
                return Optional.empty();
            }
        };
        var runtime = new SimulationRuntime(config, corpus);

        String qnGood = "event-emitter.good";
        String qnBad = "event-emitter.bad";

        CloudEvent goodEvent = CloudEventBuilder.v1()
                .withId("g")
                .withType("good.event")
                .withSource(URI.create("/test"))
                .withExtension("tenancyid", "t1")
                .build();
        corpus.seed(qnGood, List.of(new InvocationRecord<>(
                "t1", null,
                new EventTrigger("good.event", "t1", Map.of()),
                goodEvent, Instant.now())));

        List<CloudEvent> emitted = new ArrayList<>();
        var emitter = new SimulatedEventEmitter(runtime, emitted::add,
                List.of(
                        new EventSourceConfig(qnBad, "bad.event", "t1"),
                        new EventSourceConfig(qnGood, "good.event", "t1")));

        EmissionResult result = emitter.tick();

        assertThat(result.emittedCount()).isEqualTo(1);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.failures().get(0).qualifiedName()).isEqualTo(qnBad);
        assertThat(emitted).hasSize(1);
    }

    @Test
    void tickWithMultipleSourcesEmitsAll() {
        var corpus = new InMemorySimulationCorpus<EventTrigger, CloudEvent>();
        var config = stubConfig(Optional.of("sequential"), Optional.empty());
        var runtime = new SimulationRuntime(config, corpus);

        String qn1 = "event-emitter.a";
        String qn2 = "event-emitter.b";

        for (String qn : List.of(qn1, qn2)) {
            CloudEvent ce = CloudEventBuilder.v1()
                    .withId("id")
                    .withType(qn + ".event")
                    .withSource(URI.create("/test"))
                    .withExtension("tenancyid", "t1")
                    .build();
            corpus.seed(qn, List.of(new InvocationRecord<>(
                    "t1", null,
                    new EventTrigger(qn + ".event", "t1", Map.of()),
                    ce, Instant.now())));
        }

        List<CloudEvent> emitted = new ArrayList<>();
        var emitter = new SimulatedEventEmitter(runtime, emitted::add,
                List.of(
                        new EventSourceConfig(qn1, qn1 + ".event", "t1"),
                        new EventSourceConfig(qn2, qn2 + ".event", "t1")));

        EmissionResult result = emitter.tick();

        assertThat(result.emittedCount()).isEqualTo(2);
        assertThat(emitted).hasSize(2);
    }

    @Test
    void defaultKeyExtractorUsesTypeAndTenancy() {
        var extractor = SimulatedEventEmitter.defaultKeyExtractor();
        var trigger = new EventTrigger("io.casehub.work.workitem.completed", "tenant-1", Map.of());

        String key = extractor.extract(trigger);

        assertThat(key).isEqualTo("io.casehub.work.workitem.completed::tenant-1");
    }

    // --- helpers ---

    private static SimulationConfig stubConfig(final Optional<String> strategy,
                                                final Optional<ExhaustionPolicy> exhaustion) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qn) {
                return strategy;
            }

            @Override
            public boolean captureEnabled(final String qn) {
                return false;
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qn) {
                return exhaustion;
            }
        };
    }
}
