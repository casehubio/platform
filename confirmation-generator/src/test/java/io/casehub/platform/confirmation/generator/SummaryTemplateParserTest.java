package io.casehub.platform.confirmation.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryTemplateParserTest {

    @Test
    void parse_simpleParam() {
        List<String> refs = SummaryTemplateParser.extractReferences(
            "Payment of ${amount}");
        assertThat(refs).containsExactly("amount");
    }

    @Test
    void parse_multipleParams() {
        List<String> refs = SummaryTemplateParser.extractReferences(
            "Payment of ${amount} ${currency} to ${destination}");
        assertThat(refs).containsExactly("amount", "currency", "destination");
    }

    @Test
    void parse_dotNotation() {
        List<String> refs = SummaryTemplateParser.extractReferences(
            "Payment of ${request.amount}");
        assertThat(refs).containsExactly("request.amount");
    }

    @Test
    void parse_noRefs() {
        List<String> refs = SummaryTemplateParser.extractReferences(
            "Static summary with no references");
        assertThat(refs).isEmpty();
    }

    @Test
    void toJavaExpression_simpleParam() {
        String expr = SummaryTemplateParser.toJavaStringExpression(
            "Payment of ${amount}", List.of("amount"));
        assertThat(expr).isEqualTo("\"Payment of \" + String.valueOf(amount)");
    }

    @Test
    void toJavaExpression_dotNotation() {
        String expr = SummaryTemplateParser.toJavaStringExpression(
            "Payment of ${request.amount}", List.of("request.amount"));
        assertThat(expr).isEqualTo(
            "\"Payment of \" + String.valueOf(request.amount())");
    }

    @Test
    void toJavaExpression_multipleParams() {
        String expr = SummaryTemplateParser.toJavaStringExpression(
            "Pay ${amount} ${currency}",
            List.of("amount", "currency"));
        assertThat(expr).isEqualTo(
            "\"Pay \" + String.valueOf(amount) + \" \" + String.valueOf(currency)");
    }

    @Test
    void toAccessor_simpleParam() {
        assertThat(SummaryTemplateParser.toAccessor("amount")).isEqualTo("amount");
    }

    @Test
    void toAccessor_dotNotation() {
        assertThat(SummaryTemplateParser.toAccessor("request.amount"))
            .isEqualTo("request.amount()");
    }
}
