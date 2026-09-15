package io.casehub.platform.simulation.memory;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.generated.SimulatedCaseMemoryStore;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedCaseMemoryStoreTest {

    private InMemorySimulationCorpus<Object, Object> corpus;
    private StubCaseMemoryStore delegate;

    static class StubCaseMemoryStore implements CaseMemoryStore {
        final List<MemoryInput> stored = new ArrayList<>();

        @Override
        public String store(final MemoryInput input) {
            stored.add(input);
            return "stub-id-" + stored.size();
        }

        @Override
        public List<Memory> query(final MemoryQuery query) {
            return List.of(new Memory("m1", Subject.of("user", "e1"),
                    new MemoryDomain("test"), query.tenantId(), null,
                    "stub response", Map.of(), Instant.now(),
                    null, null, null, null, null, Set.of()));
        }

        @Override
        public int erase(final EraseRequest request) {
            return 1;
        }

        @Override
        public Set<MemoryCapability> capabilities() {
            return Set.of(MemoryCapability.CHRONOLOGICAL_ORDER,
                    MemoryCapability.DOMAIN_SCOPED);
        }
    }

    static class StubPrincipal implements CurrentPrincipal {
        @Override public String actorId() { return "test-actor"; }
        @Override public String tenancyId() { return "tenant-1"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public boolean isCrossTenantAdmin() { return false; }
    }

    @BeforeEach
    void setUp() {
        corpus = new InMemorySimulationCorpus<>();
        delegate = new StubCaseMemoryStore();
    }

    @Test
    void queryWithStrategyReturnsCorpusData() {
        var config = new MapSimulationConfig(
                Map.of("case-memory-store.query", "sequential"));
        var runtime = new SimulationRuntime(config, corpus);

        var expectedMemory = new Memory("sim-1", Subject.of("user", "e1"),
                new MemoryDomain("test"), "tenant-1", null,
                "simulated response", Map.of(), Instant.now(),
                null, null, null, null, null, Set.of());
        corpus.seed("case-memory-store.query",
                List.of(new InvocationRecord<>("tenant-1", null,
                        null, List.of(expectedMemory), Instant.now())));

        var decorator = createDecorator(runtime);

        var query = new MemoryQuery(
                List.of(Subject.of("user", "e1")),
                new MemoryDomain("test"), "tenant-1", null, null,
                10, null, MemoryOrder.CHRONOLOGICAL, null);

        @SuppressWarnings("unchecked")
        List<Memory> result = decorator.query(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("simulated response");
        assertThat(delegate.stored).isEmpty();
    }

    @Test
    void queryWithoutStrategyDelegatesToBackend() {
        var config = new MapSimulationConfig(Map.of());
        var runtime = new SimulationRuntime(config, corpus);
        var decorator = createDecorator(runtime);

        var query = new MemoryQuery(
                List.of(Subject.of("user", "e1")),
                new MemoryDomain("test"), "tenant-1", null, null,
                10, null, MemoryOrder.CHRONOLOGICAL, null);

        List<Memory> result = decorator.query(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("stub response");
    }

    @Test
    void storeWithCaptureRecordsToCorpus() {
        var config = new MapSimulationConfig(Map.of(),
                Set.of("case-memory-store.store"));
        var runtime = new SimulationRuntime(config, corpus);
        var decorator = createDecorator(runtime);

        var input = new MemoryInput(
                Subject.of("user", "e1"), new MemoryDomain("test"),
                "tenant-1", null, "test memory",
                Map.of(), null, null, null, null, null, null);

        String id = decorator.store(input);

        assertThat(id).isEqualTo("stub-id-1");
        assertThat(delegate.stored).hasSize(1);
        assertThat(corpus.list("case-memory-store.store")).hasSize(1);
    }

    @Test
    void capabilitiesDelegatesToBackend() {
        var config = new MapSimulationConfig(Map.of());
        var runtime = new SimulationRuntime(config, corpus);
        var decorator = createDecorator(runtime);

        Set<MemoryCapability> caps = decorator.capabilities();

        assertThat(caps).containsExactlyInAnyOrder(
                MemoryCapability.CHRONOLOGICAL_ORDER,
                MemoryCapability.DOMAIN_SCOPED);
    }

    @Test
    void eraseWithStrategyReturnsCorpusData() {
        var config = new MapSimulationConfig(
                Map.of("case-memory-store.erase", "sequential"));
        var runtime = new SimulationRuntime(config, corpus);

        corpus.seed("case-memory-store.erase",
                List.of(new InvocationRecord<>("tenant-1", null, null, 42, Instant.now())));

        var decorator = createDecorator(runtime);
        var request = new EraseRequest(Subject.of("user", "e1"),
                new MemoryDomain("test"), "tenant-1", null);

        int result = decorator.erase(request);

        assertThat(result).isEqualTo(42);
    }

    // --- helpers ---

    static class MapSimulationConfig implements SimulationConfig {
        private final Map<String, String> strategies;
        private final Set<String> captures;

        MapSimulationConfig(final Map<String, String> strategies) {
            this(strategies, Set.of());
        }

        MapSimulationConfig(final Map<String, String> strategies, final Set<String> captures) {
            this.strategies = strategies;
            this.captures = captures;
        }

        @Override
        public Optional<String> strategyFor(final String qualifiedName) {
            return Optional.ofNullable(strategies.get(qualifiedName));
        }

        @Override
        public boolean captureEnabled(final String qualifiedName) {
            return captures.contains(qualifiedName);
        }

        @Override
        public Optional<ExhaustionPolicy> exhaustionPolicy(final String qualifiedName) {
            return Optional.empty();
        }
    }

    private SimulatedCaseMemoryStore createDecorator(final SimulationRuntime runtime) {
        try {
            var ctor = SimulatedCaseMemoryStore.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            var instance = ctor.newInstance();

            var delegateField = SimulatedCaseMemoryStore.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            delegateField.set(instance, delegate);

            var simField = SimulatedCaseMemoryStore.class.getDeclaredField("simulation");
            simField.setAccessible(true);
            simField.set(instance, runtime);

            var principalField = SimulatedCaseMemoryStore.class.getDeclaredField("currentPrincipal");
            principalField.setAccessible(true);
            principalField.set(instance, new StubPrincipal());

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create decorator", e);
        }
    }
}
