package io.casehub.platform.view;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.view.SubjectViewEvent;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.ViewEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectViewEvaluatorTest {

    private final SubjectViewEvaluator evaluator = new SubjectViewEvaluator();

    private SubjectViewSpec view(String name, String pattern) {
        return new SubjectViewSpec(UUID.randomUUID(), name, "t1", pattern,
                                   Path.root(), null, null, null, null);
    }

    @Test
    void evaluateMembership_matchesWildcard() {
        var v1 = view("iot-all", "iot/**");
        var v2 = view("legal", "legal/**");

        var result = evaluator.evaluateMembership(Set.of("iot/triage/hvac"), List.of(v1, v2));

        assertThat(result).containsEntry(v1.id(), "iot-all");
        assertThat(result).doesNotContainKey(v2.id());
    }

    @Test
    void evaluateMembership_multipleViewsMatch() {
        var v1 = view("iot-all", "iot/**");
        var v2 = view("iot-triage", "iot/triage/*");

        var result = evaluator.evaluateMembership(Set.of("iot/triage/hvac"), List.of(v1, v2));

        assertThat(result).hasSize(2);
        assertThat(result).containsEntry(v1.id(), "iot-all");
        assertThat(result).containsEntry(v2.id(), "iot-triage");
    }

    @Test
    void evaluateMembership_noMatch() {
        var result = evaluator.evaluateMembership(
            Set.of("iot/triage/hvac"), List.of(view("legal", "legal/**")));
        assertThat(result).isEmpty();
    }

    @Test
    void evaluateMembership_emptyLabels() {
        var result = evaluator.evaluateMembership(Set.of(), List.of(view("all", "iot/**")));
        assertThat(result).isEmpty();
    }

    @Test
    void evaluateMembership_emptyViews() {
        var result = evaluator.evaluateMembership(Set.of("iot/triage/hvac"), List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void diff_added() {
        var viewId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();
        Map<UUID, String> before = Map.of();
        Map<UUID, String> after = Map.of(viewId, "iot-triage");

        var events = evaluator.diff(subjectId, "t1", before, after);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(ViewEventType.ADDED);
        assertThat(events.get(0).viewName()).isEqualTo("iot-triage");
        assertThat(events.get(0).subjectId()).isEqualTo(subjectId);
    }

    @Test
    void diff_removed() {
        var viewId = UUID.randomUUID();
        Map<UUID, String> before = Map.of(viewId, "iot-triage");
        Map<UUID, String> after = Map.of();

        var events = evaluator.diff(UUID.randomUUID(), "t1", before, after);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(ViewEventType.REMOVED);
    }

    @Test
    void diff_noChange() {
        var viewId = UUID.randomUUID();
        Map<UUID, String> state = Map.of(viewId, "v");

        var events = evaluator.diff(UUID.randomUUID(), "t1", state, state);

        assertThat(events).isEmpty();
    }

    @Test
    void diff_addedAndRemoved() {
        var removedId = UUID.randomUUID();
        var addedId = UUID.randomUUID();
        Map<UUID, String> before = Map.of(removedId, "old-view");
        Map<UUID, String> after = Map.of(addedId, "new-view");

        var events = evaluator.diff(UUID.randomUUID(), "t1", before, after);

        assertThat(events).hasSize(2);
        assertThat(events).extracting(SubjectViewEvent::type)
            .containsExactlyInAnyOrder(ViewEventType.ADDED, ViewEventType.REMOVED);
    }
}
