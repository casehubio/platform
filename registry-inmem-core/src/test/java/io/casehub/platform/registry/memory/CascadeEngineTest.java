package io.casehub.platform.registry.memory;

import io.casehub.platform.api.registry.CascadeAction;
import io.casehub.platform.api.registry.CascadeEngine;
import io.casehub.platform.api.registry.CascadeRule;
import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEntry;
import io.casehub.platform.api.registry.RegistryEvent;
import io.casehub.platform.api.registry.Relationship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeEngineTest {

    private CascadeEngine              engine;
    private Map<String, RegistryEntry> entries;
    private List<Relationship> relationships;
    private List<RegistryEvent> events;

    @BeforeEach
    void setUp() {
        engine = new CascadeEngine();
        entries = new HashMap<>();
        relationships = new ArrayList<>();
        events = new ArrayList<>();
    }

    private RegistryEntry entry(String id) {
        return new RegistryEntry(id, "service", "default", "tenant-1",
                Map.of(), Instant.now(), null, Duration.ofSeconds(30),
                HealthStatus.HEALTHY, null);
    }

    private CascadeEngine.EntryMutator mutator() {
        return new CascadeEngine.EntryMutator() {
            @Override
            public void deregister(String id) {
                entries.remove(id);
            }

            @Override
            public void markOrphaned(String id, Instant now) {
                var e = entries.get(id);
                if (e != null) entries.put(id, e.withOrphaned(now));
            }
        };
    }

    private List<Relationship> outbound(String sourceId) {
        return relationships.stream()
                .filter(r -> r.sourceId().equals(sourceId)).toList();
    }

    @Test
    void deregisterCascade_removesTarget() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.DEREGISTER));

        var effects = engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isFalse();
        assertThat(effects).hasSize(1);
        assertThat(effects.get(0).action()).isEqualTo(CascadeAction.DEREGISTER);
        assertThat(effects.get(0).failure()).isNull();
    }

    @Test
    void markOrphanedCascade_setsOrphanedAt() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.MARK_ORPHANED));

        engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.get("B").isOrphaned()).isTrue();
        assertThat(events).anyMatch(e -> e.kind() == RegistryEvent.EventKind.ORPHANED);
    }

    @Test
    void notifyCascade_firesEventNoStateChange() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.NOTIFY));

        engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isTrue();
        assertThat(entries.get("B").isOrphaned()).isFalse();
        assertThat(events).anyMatch(e -> e.kind() == RegistryEvent.EventKind.CASCADE_NOTIFIED);
    }

    @Test
    void noneCascade_doesNothing() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.NONE));

        var effects = engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isTrue();
        assertThat(effects).isEmpty();
    }

    @Test
    void recursiveCascade_A_B_C() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        entries.put("C", entry("C"));
        relationships.add(new Relationship("A", "B", "owns"));
        relationships.add(new Relationship("B", "C", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.DEREGISTER));

        var effects = engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isFalse();
        assertThat(entries.containsKey("C")).isFalse();
    }

    @Test
    void cycleDetection_A_B_A() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        relationships.add(new Relationship("B", "A", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.DEREGISTER));

        var effects = engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isFalse();
        assertThat(effects).hasSize(1);
    }

    @Test
    void depthLimit_stopsRecursion() {
        for (int i = 0; i < 15; i++) {
            entries.put("n" + i, entry("n" + i));
            if (i > 0) relationships.add(new Relationship("n" + (i - 1), "n" + i, "owns"));
        }
        var rules = List.of(new CascadeRule("owns", CascadeAction.DEREGISTER));

        engine.applyCascade("n0", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("n0")), 5);

        assertThat(entries.containsKey("n5")).isFalse();
        assertThat(entries.containsKey("n6")).isTrue();
    }

    @Test
    void noMatchingRule_noCascade() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        var rules = List.of(new CascadeRule("contains", CascadeAction.DEREGISTER));

        var effects = engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isTrue();
        assertThat(effects).isEmpty();
    }

    @Test
    void actionSelectorUsesExpiredAction() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.DEREGISTER,
                CascadeAction.NOTIFY, null));

        engine.applyCascade("A", rules,
                CascadeRule::effectiveOnSourceExpired,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isTrue();
        assertThat(events).anyMatch(e -> e.kind() == RegistryEvent.EventKind.CASCADE_NOTIFIED);
    }

    @Test
    void multipleTargets_allCascaded() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        entries.put("C", entry("C"));
        relationships.add(new Relationship("A", "B", "owns"));
        relationships.add(new Relationship("A", "C", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.DEREGISTER));

        var effects = engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(entries.containsKey("B")).isFalse();
        assertThat(entries.containsKey("C")).isFalse();
        assertThat(effects).hasSize(2);
    }

    @Test
    void partialFailure_reportsFailedTarget() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));
        var rules = List.of(new CascadeRule("owns", CascadeAction.DEREGISTER));

        var failingMutator = new CascadeEngine.EntryMutator() {
            @Override
            public void deregister(String id) {
                throw new RuntimeException("storage failure");
            }

            @Override
            public void markOrphaned(String id, Instant now) {}
        };

        var effects = engine.applyCascade("A", rules,
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, failingMutator, events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(effects).hasSize(1);
        assertThat(effects.get(0).failure()).contains("storage failure");
    }

    @Test
    void emptyRules_noCascade() {
        entries.put("A", entry("A"));
        entries.put("B", entry("B"));
        relationships.add(new Relationship("A", "B", "owns"));

        var effects = engine.applyCascade("A", List.of(),
                CascadeRule::onSourceDeregister,
                id -> Optional.ofNullable(entries.get(id)),
                this::outbound, mutator(), events::add,
                new HashSet<>(Set.of("A")), 10);

        assertThat(effects).isEmpty();
        assertThat(entries.containsKey("B")).isTrue();
    }
}
