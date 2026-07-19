package io.casehub.platform.view;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.view.SubjectViewEvent;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.platform.api.view.ViewEventType;
import io.casehub.platform.api.view.ViewMembershipTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectViewOrchestratorTest {

    private SubjectViewEvaluator evaluator;
    private StubViewStore viewStore;
    private StubTracker tracker;
    private SubjectViewOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        evaluator = new SubjectViewEvaluator();
        viewStore = new StubViewStore();
        tracker = new StubTracker();
        orchestrator = new SubjectViewOrchestrator();
        orchestrator.evaluator = evaluator;
        orchestrator.viewStore = viewStore;
        orchestrator.tracker = tracker;
        orchestrator.cacheTtlSeconds = 0;
    }

    private SubjectViewSpec view(String name, String pattern) {
        return viewStore.save(new SubjectViewSpec(
            null, name, "t1", pattern,
            null, null, null, null, null));
    }

    @Test
    void evaluateAndTrack_addsToView() {
        var v = view("iot-all", "iot/**");
        var subjectId = UUID.randomUUID();

        var events = orchestrator.evaluateAndTrack(
            subjectId, "t1", Set.of("iot/triage/hvac"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(ViewEventType.ADDED);
        assertThat(events.get(0).viewId()).isEqualTo(v.id());
        assertThat(tracker.getLastKnownMembership(subjectId))
            .containsKey(v.id());
    }

    @Test
    void evaluateAndTrack_changedOnSecondCall() {
        view("iot-all", "iot/**");
        var subjectId = UUID.randomUUID();

        orchestrator.evaluateAndTrack(subjectId, "t1", Set.of("iot/triage/hvac"));
        var events = orchestrator.evaluateAndTrack(
            subjectId, "t1", Set.of("iot/triage/hvac"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(ViewEventType.CHANGED);
    }

    @Test
    void evaluateAndTrack_removedWhenLabelsNoLongerMatch() {
        view("iot-all", "iot/**");
        var subjectId = UUID.randomUUID();

        orchestrator.evaluateAndTrack(subjectId, "t1", Set.of("iot/triage/hvac"));
        var events = orchestrator.evaluateAndTrack(
            subjectId, "t1", Set.of("legal/compliance"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(ViewEventType.REMOVED);
    }

    @Test
    void evaluateAndTrackBatch_multipleSubjects() {
        var v = view("iot-all", "iot/**");
        var s1 = UUID.randomUUID();
        var s2 = UUID.randomUUID();

        var result = orchestrator.evaluateAndTrackBatch(
            Map.of(s1, Set.of("iot/triage"), s2, Set.of("legal/foo")), "t1");

        assertThat(result.get(s1)).hasSize(1);
        assertThat(result.get(s1).get(0).type()).isEqualTo(ViewEventType.ADDED);
        assertThat(result.get(s2)).isEmpty();
    }

    @Test
    void saveView_returnsPersistedSpec() {
        var spec = new SubjectViewSpec(null, "test", "t1",
            "iot/**", null, null, null, null, null);
        var saved = orchestrator.saveView(spec);
        assertThat(saved.id()).isNotNull();
        assertThat(viewStore.findById(saved.id())).isPresent();
    }

    @Test
    void deleteView_removesFromStore() {
        var saved = orchestrator.saveView(new SubjectViewSpec(null, "test", "t1",
            "iot/**", null, null, null, null, null));
        orchestrator.deleteView(saved.id());
        assertThat(viewStore.findById(saved.id())).isEmpty();
    }

    @Test
    void deleteView_nonExistentIsNoOp() {
        orchestrator.deleteView(UUID.randomUUID());
    }

    @Test
    void caching_viewsCachedWhenTtlPositive() {
        orchestrator.cacheTtlSeconds = 60;
        view("iot-all", "iot/**");
        var s = UUID.randomUUID();

        orchestrator.evaluateAndTrack(s, "t1", Set.of("iot/x"));
        viewStore.save(new SubjectViewSpec(null, "new-view", "t1",
            "legal/**", null, null, null, null, null));
        var events = orchestrator.evaluateAndTrack(s, "t1", Set.of("iot/x"));

        assertThat(events).extracting(SubjectViewEvent::type)
            .containsOnly(ViewEventType.CHANGED);
        assertThat(events).hasSize(1);
    }

    @Test
    void caching_saveViewInvalidatesCache() {
        orchestrator.cacheTtlSeconds = 60;
        view("iot-all", "iot/**");
        var s = UUID.randomUUID();

        orchestrator.evaluateAndTrack(s, "t1", Set.of("iot/x"));
        orchestrator.saveView(new SubjectViewSpec(null, "iot-triage", "t1",
                                                  "iot/*", null, null, null, null, null));
        var events = orchestrator.evaluateAndTrack(s, "t1", Set.of("iot/x"));

        assertThat(events).hasSize(2);
        assertThat(events).extracting(SubjectViewEvent::type)
                          .containsExactlyInAnyOrder(ViewEventType.CHANGED, ViewEventType.ADDED);}

    @Test
    void evaluateAndTrack_withScope() {
        var euView = viewStore.save(new SubjectViewSpec(null, "eu-iot", "t1",
            "iot/**", Path.of("eu"), null, null, null, null));
        var usView = viewStore.save(new SubjectViewSpec(null, "us-iot", "t1",
            "iot/**", Path.of("us"), null, null, null, null));

        var events = orchestrator.evaluateAndTrack(
            UUID.randomUUID(), "t1", Set.of("iot/triage"), Path.of("eu", "germany"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).viewId()).isEqualTo(euView.id());
    }

    @Test
    void evaluateAndTrackBatch_withScopeResolver() {
        var euView = viewStore.save(new SubjectViewSpec(null, "eu-iot", "t1",
            "iot/**", Path.of("eu"), null, null, null, null));
        var s1 = UUID.randomUUID();
        var s2 = UUID.randomUUID();

        Map<UUID, Path> scopes = Map.of(
            s1, Path.of("eu", "germany"),
            s2, Path.of("us", "california"));

        var result = orchestrator.evaluateAndTrackBatch(
            Map.of(s1, Set.of("iot/x"), s2, Set.of("iot/x")),
            "t1", scopes::get);

        assertThat(result.get(s1)).hasSize(1);
        assertThat(result.get(s2)).isEmpty();
    }

    static class StubViewStore implements SubjectViewStore {
        private final Map<UUID, SubjectViewSpec> store = new LinkedHashMap<>();

        @Override
        public SubjectViewSpec save(SubjectViewSpec spec) {
            UUID id = spec.id() != null ? spec.id() : UUID.randomUUID();
            var saved = new SubjectViewSpec(id, spec.name(), spec.tenancyId(),
                spec.labelPattern(), spec.scope(), spec.sortField(),
                spec.sortDirection(), spec.additionalConditions(),
                Instant.now());
            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<SubjectViewSpec> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<SubjectViewSpec> findByTenancy(String tenancyId) {
            return store.values().stream()
                .filter(s -> s.tenancyId().equals(tenancyId)).toList();
        }

        @Override
        public boolean delete(UUID id) {return store.remove(id) != null;}
    }

    static class StubTracker implements ViewMembershipTracker {
        private final Map<UUID, Map<UUID, String>> state = new HashMap<>();

        @Override
        public Map<UUID, String> getLastKnownMembership(UUID subjectId) {
            return state.getOrDefault(subjectId, Map.of());
        }

        @Override
        public void updateMembership(UUID subjectId, Map<UUID, String> m) {
            state.put(subjectId, Map.copyOf(m));
        }

        @Override
        public void removeMembership(UUID subjectId) { state.remove(subjectId); }
    }
}
