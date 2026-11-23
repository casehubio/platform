package io.casehub.extraction.nlp;

import io.casehub.extraction.MessageMatch;
import io.casehub.extraction.MessageSchema;
import io.casehub.extraction.VariableConstraint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwoTierMessageMatcherTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void exactMatchUsesRegexTierWithScoreOne() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .build();
        var matcher = new TwoTierMessageMatcher(schema, tokenizer);
        var result = (MessageMatch.Matched) matcher.match("I approve this design");
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.fields().get("decision")).isEqualTo("approve");
        assertThat(result.type()).isEqualTo("vote");
    }

    @Test
    void grammaticalVariationMatchesViaNlpTier() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .build();
        var matcher = new TwoTierMessageMatcher(schema, tokenizer);
        var result = matcher.match("I approved this design");
        assertThat(result).isInstanceOf(MessageMatch.Matched.class);
        var matched = (MessageMatch.Matched) result;
        assertThat(matched.fields().get("decision")).isEqualToIgnoringCase("approve");
        assertThat(matched.score()).isGreaterThan(0.0);
    }

    @Test
    void noMatchReturnsUnmatched() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .build();
        var matcher = new TwoTierMessageMatcher(schema, tokenizer);
        var result = matcher.match("The weather is nice today");
        assertThat(result).isInstanceOf(MessageMatch.Unmatched.class);
    }

    @Test
    void thresholdRejectsLowScoreMatches() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject} because ${rationale}")
                    .variable("decision", VariableConstraint.enumOf("approve"))
                    .done()
                .build();
        var matcher = new TwoTierMessageMatcher(schema, tokenizer, 0.95);
        var result = matcher.match("approve");
        assertThat(result).isInstanceOf(MessageMatch.Unmatched.class);
    }

    @Test
    void regexTierTriedBeforeNlp() {
        var schema = MessageSchema.builder()
                .message("greet")
                    .template("Hello ${name}")
                    .done()
                .build();
        var matcher = new TwoTierMessageMatcher(schema, tokenizer);
        var result = (MessageMatch.Matched) matcher.match("Hello World");
        assertThat(result.score()).isEqualTo(1.0);
    }
}
