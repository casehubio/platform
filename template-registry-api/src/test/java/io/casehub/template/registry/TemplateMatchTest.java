package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateMatchTest {

    @Test
    void matchedWithValidScore() {
        var matched = new TemplateMatch.Matched(
            "ns", "vote", 0, Map.of("decision", "approve"), 0.9, 0);
        assertThat(matched.namespace()).isEqualTo("ns");
        assertThat(matched.templateName()).isEqualTo("vote");
        assertThat(matched.fields()).containsEntry("decision", "approve");
        assertThat(matched.score()).isEqualTo(0.9);
    }

    @Test
    void matchedScoreExactlyOne() {
        var matched = new TemplateMatch.Matched(
            "ns", "vote", 0, Map.of(), 1.0, 0);
        assertThat(matched.score()).isEqualTo(1.0);
    }

    @Test
    void matchedScoreZeroThrows() {
        assertThatThrownBy(() -> new TemplateMatch.Matched(
            "ns", "vote", 0, Map.of(), 0.0, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchedScoreNegativeThrows() {
        assertThatThrownBy(() -> new TemplateMatch.Matched(
            "ns", "vote", 0, Map.of(), -0.1, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchedScoreAboveOneThrows() {
        assertThatThrownBy(() -> new TemplateMatch.Matched(
            "ns", "vote", 0, Map.of(), 1.1, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchedFieldsDefensivelyCopied() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("decision", "approve");
        var matched = new TemplateMatch.Matched("ns", "vote", 0, fields, 1.0, 0);
        fields.put("extra", "value");
        assertThat(matched.fields()).hasSize(1);
    }

    @Test
    void matchedNullNamespaceThrows() {
        assertThatThrownBy(() -> new TemplateMatch.Matched(
            null, "vote", 0, Map.of(), 1.0, 0))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nearMissNullInputThrows() {
        assertThatThrownBy(() -> new TemplateMatch.NearMiss(
            "ns", null, "vote", "format"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void unmatchedNullInputThrows() {
        assertThatThrownBy(() -> new TemplateMatch.Unmatched(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void unmatchedHoldsInput() {
        var unmatched = new TemplateMatch.Unmatched("some text");
        assertThat(unmatched.input()).isEqualTo("some text");
    }
}
