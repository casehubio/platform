package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookModuleBridgeTest {

    private final PlaybookModuleBridge bridge = new PlaybookModuleBridge();

    @Test
    void toSectionsWrapsContentInStepsSection() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", "test");
        content.put("steps", List.of(Map.of("agent-invoke", Map.of("prompt", "hi"))));

        var sections = bridge.toSections(content);

        assertThat(sections).containsKey("steps");
    }

    @Test
    void fromSectionsReconstructsContentWithExpandedSections() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", "test");
        content.put("steps", List.of(Map.of("agent-invoke", Map.of("prompt", "hi"))));

        bridge.toSections(content);
        Map<String, Map<String, Object>> sections = Map.of(
                "steps", Map.of("g.say-hello", List.of(Map.of("agent-invoke", Map.of("prompt", "hello")))));
        var reconstructed = bridge.fromSections(sections);

        assertThat(reconstructed).containsKey("name");
        assertThat(reconstructed).containsKey(PlaybookModuleBridge.EXPANDED_STEP_SECTIONS_KEY);
    }

    @SuppressWarnings("unchecked")
    @Test
    void expandUseStepsInlinesModuleSteps() {
        List<Map<String, Object>> moduleSteps = List.of(
                Map.of("agent-invoke", Map.of("prompt", "from module")));

        Map<String, Object> expandedSections = Map.of(
                "lifecycle.open-session", (Object) moduleSteps);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("steps", new ArrayList<>(List.of(
                Map.of("use", "lifecycle.open-session"),
                Map.of("agent-invoke", Map.of("prompt", "inline step")))));

        Map<String, Object> result = PlaybookModuleBridge.expandUseSteps(
                content, expandedSections);

        var steps = (List<Map<String, Object>>) result.get("steps");
        assertThat(steps).hasSize(2);
        assertThat(((Map<String, Object>) steps.get(0).get("agent-invoke")).get("prompt"))
                .isEqualTo("from module");
        assertThat(((Map<String, Object>) steps.get(1).get("agent-invoke")).get("prompt"))
                .isEqualTo("inline step");
    }

    @SuppressWarnings("unchecked")
    @Test
    void expandUseStepsInlinesMultiStepSequence() {
        List<Map<String, Object>> moduleSteps = List.of(
                Map.of("agent-invoke", Map.of("prompt", "step 1")),
                Map.of("agent-invoke", Map.of("prompt", "step 2")));

        Map<String, Object> expandedSections = Map.of(
                "g.multi", (Object) moduleSteps);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("steps", new ArrayList<>(List.of(
                Map.of("use", "g.multi"))));

        Map<String, Object> result = PlaybookModuleBridge.expandUseSteps(
                content, expandedSections);

        var steps = (List<Map<String, Object>>) result.get("steps");
        assertThat(steps).hasSize(2);
    }

    @Test
    void expandUseStepsThrowsForUnknownSequence() {
        Map<String, Object> expandedSections = Map.of(
                "lifecycle.open-session", (Object) List.of());

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("steps", new ArrayList<>(List.of(
                Map.of("use", "lifecycle.nonexistent"))));

        assertThatThrownBy(() -> PlaybookModuleBridge.expandUseSteps(
                content, expandedSections))
                .isInstanceOf(UnknownStepSequenceException.class)
                .hasMessageContaining("lifecycle.nonexistent");
    }

    @Test
    void expandUseStepsThrowsForMissingDotInReference() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("steps", new ArrayList<>(List.of(
                Map.of("use", "no-dot-reference"))));

        assertThatThrownBy(() -> PlaybookModuleBridge.expandUseSteps(
                content, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alias.step-name");
    }

    @SuppressWarnings("unchecked")
    @Test
    void expandUseStepsNoOpsWhenNoUseReferences() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("steps", List.of(
                Map.of("agent-invoke", Map.of("prompt", "hi"))));

        Map<String, Object> result = PlaybookModuleBridge.expandUseSteps(
                content, Map.of());

        var steps = (List<Map<String, Object>>) result.get("steps");
        assertThat(steps).hasSize(1);
    }

    @Test
    void expandUseStepsReturnsContentWhenNoSteps() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", "empty");

        Map<String, Object> result = PlaybookModuleBridge.expandUseSteps(
                content, Map.of());

        assertThat(result).containsEntry("name", "empty");
        assertThat(result).doesNotContainKey("steps");
    }
}
