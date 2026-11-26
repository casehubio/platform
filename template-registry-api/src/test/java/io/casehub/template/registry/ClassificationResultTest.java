package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassificationResultTest {

    @Test
    void matched_requiresNonNullTemplateName() {
        assertThatThrownBy(() -> new ClassificationResult.Matched(
            null, Map.of(), "pattern", 0.9))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void matched_requiresNonNullFields() {
        assertThatThrownBy(() -> new ClassificationResult.Matched(
            "t1", null, "pattern", 0.9))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void matched_requiresNonNullSuggestedVariation() {
        assertThatThrownBy(() -> new ClassificationResult.Matched(
            "t1", Map.of(), null, 0.9))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void matched_fieldsAreImmutable() {
        var fields = new HashMap<String, String>();
        fields.put("k", "v");
        var m = new ClassificationResult.Matched("t1", fields, "pattern", 0.9);
        assertThatThrownBy(() -> m.fields().put("k2", "v2"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void confidence_rejectsBelowZero() {
        assertThatThrownBy(() -> new ClassificationResult.Matched(
            "t1", Map.of(), "pattern", -0.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidence_rejectsAboveOne() {
        assertThatThrownBy(() -> new ClassificationResult.Matched(
            "t1", Map.of(), "pattern", 1.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidence_acceptsBoundaryValues() {
        var zero = new ClassificationResult.Matched("t1", Map.of(), "p", 0.0);
        assertThat(zero.confidence()).isEqualTo(0.0);

        var one = new ClassificationResult.Matched("t1", Map.of(), "p", 1.0);
        assertThat(one.confidence()).isEqualTo(1.0);
    }

    @Test
    void unmatched_isSealed() {
        ClassificationResult result = new ClassificationResult.Unmatched();
        assertThat(result).isInstanceOf(ClassificationResult.Unmatched.class);
    }
}
