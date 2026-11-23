package io.casehub.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexMessageMatcherTest {

    @Test
    void matchesExactTemplateAndExtractsVariables() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .variable("subject", VariableConstraint.string())
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = matcher.match("I approve this design");
        assertThat(result).isInstanceOf(MessageMatch.Matched.class);
        var matched = (MessageMatch.Matched) result;
        assertThat(matched.type()).isEqualTo("vote");
        assertThat(matched.templateIndex()).isEqualTo(0);
        assertThat(matched.fields()).containsEntry("decision", "approve");
        assertThat(matched.fields()).containsEntry("subject", "design");
        assertThat(matched.score()).isEqualTo(1.0);
    }

    @Test
    void rejectsInputThatDoesNotMatchAnyTemplate() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = matcher.match("completely unrelated text");
        assertThat(result).isInstanceOf(MessageMatch.Unmatched.class);
    }

    @Test
    void enumConstraintRejectsInvalidValue() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = matcher.match("I celebrate this design");
        assertThat(result).isInstanceOf(MessageMatch.Unmatched.class);
    }

    @Test
    void matchesFirstMatchingTemplateInMultiTemplateType() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .template("${decision}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = matcher.match("I approve this design");
        assertThat(result).isInstanceOf(MessageMatch.Matched.class);
        assertThat(((MessageMatch.Matched) result).templateIndex()).isEqualTo(0);
    }

    @Test
    void fallsThroughToSecondTemplateWhenFirstDoesNotMatch() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .template("${decision}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = matcher.match("approve");
        assertThat(result).isInstanceOf(MessageMatch.Matched.class);
        assertThat(((MessageMatch.Matched) result).templateIndex()).isEqualTo(1);
    }

    @Test
    void matchesDifferentTypesCorrectly() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision}")
                    .variable("decision", VariableConstraint.enumOf("approve"))
                    .done()
                .message("escalate")
                    .template("Escalating to ${target}")
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        assertThat(((MessageMatch.Matched) matcher.match("I approve")).type()).isEqualTo("vote");
        assertThat(((MessageMatch.Matched) matcher.match("Escalating to manager")).type()).isEqualTo("escalate");
    }

    @Test
    void optionalSegmentsWork() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision}[ because ${rationale}]")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .variable("rationale", VariableConstraint.string().optional())
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);

        var withRationale = (MessageMatch.Matched) matcher.match("I approve because it looks good");
        assertThat(withRationale.fields()).containsEntry("decision", "approve");
        assertThat(withRationale.fields()).containsEntry("rationale", "it looks good");

        var withoutRationale = (MessageMatch.Matched) matcher.match("I approve");
        assertThat(withoutRationale.fields()).containsEntry("decision", "approve");
        assertThat(withoutRationale.fields()).doesNotContainKey("rationale");
    }

    @Test
    void enumConstraintMatchesCaseInsensitive() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("${decision}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = matcher.match("Approve");
        assertThat(result).isInstanceOf(MessageMatch.Matched.class);
        assertThat(((MessageMatch.Matched) result).fields().get("decision")).isEqualTo("Approve");
    }

    @Test
    void noConstraintForVariableMeansAnyValueAccepted() {
        var schema = MessageSchema.builder()
                .message("greet")
                    .template("Hello ${name}")
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = (MessageMatch.Matched) matcher.match("Hello World");
        assertThat(result.fields()).containsEntry("name", "World");
    }

    @Test
    void constraintFailureFallsThroughToNextType() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .done()
                .message("action")
                    .template("I ${action}")
                    .done()
                .build();
        var matcher = new RegexMessageMatcher(schema);
        var result = (MessageMatch.Matched) matcher.match("I celebrate");
        assertThat(result.type()).isEqualTo("action");
        assertThat(result.fields()).containsEntry("action", "celebrate");
    }

    @Test
    void emptyInputReturnsUnmatched() {
        var schema = MessageSchema.builder()
                                  .message("vote")
                                  .template("I ${decision}")
                                  .done()
                                  .build();
        var matcher = new RegexMessageMatcher(schema);
        assertThat(matcher.match("")).isInstanceOf(MessageMatch.Unmatched.class);
    }
}
