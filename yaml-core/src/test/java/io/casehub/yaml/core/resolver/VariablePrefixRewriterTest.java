package io.casehub.yaml.core.resolver;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VariablePrefixRewriterTest {

    private static final Set<String> KNOWN_PREFIXES = Set.of("result", "each", "machine", "signal", "channel", "corpus", "shared");
    private static final Set<String> FOR_EACH_VARS = Set.of("instrument", "trade");

    @Test
    void bareReference_rewrittenToDefaultPrefix() {
        String result = VariablePrefixRewriter.rewrite("${regime}", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("${var.regime}");
    }

    @Test
    void knownPrefix_notRewritten() {
        String result = VariablePrefixRewriter.rewrite("${result.x}", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("${result.x}");
    }

    @Test
    void forEachVar_rewrittenToEachPrefix() {
        String result = VariablePrefixRewriter.rewrite("${instrument.symbol}", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("${each.instrument.symbol}");
    }

    @Test
    void multipleReferencesInString() {
        String result = VariablePrefixRewriter.rewrite(
                "when ${regime} == 'X' && ${instrument.symbol} != null",
                "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("when ${var.regime} == 'X' && ${each.instrument.symbol} != null");
    }

    @Test
    void defaultValueSyntax_rewritesNameOnly() {
        String result = VariablePrefixRewriter.rewrite("${regime:-DEFAULT}", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("${var.regime:-DEFAULT}");
    }

    @Test
    void knownPrefixWithDefaultValue_notRewritten() {
        String result = VariablePrefixRewriter.rewrite("${result.x:-fallback}", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("${result.x:-fallback}");
    }

    @Test
    void noReferences_returnsUnchanged() {
        String result = VariablePrefixRewriter.rewrite("no references here", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("no references here");
    }

    @Test
    void emptyString_returnsEmpty() {
        String result = VariablePrefixRewriter.rewrite("", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEmpty();
    }

    @Test
    void noDottedSegment_rewrittenWithDefaultPrefix() {
        String result = VariablePrefixRewriter.rewrite("${name}", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("${var.name}");
    }

    @Test
    void sharedPrefix_notRewritten() {
        String result = VariablePrefixRewriter.rewrite("${shared.events-fired}", "var", KNOWN_PREFIXES, FOR_EACH_VARS);
        assertThat(result).isEqualTo("${shared.events-fired}");
    }

    @Test
    void forEachVarCollisionWithKnownPrefix_forEachWins() {
        Set<String> prefixes = Set.of("instrument");
        Set<String> forEachVars = Set.of("instrument");
        String result = VariablePrefixRewriter.rewrite("${instrument.symbol}", "var", prefixes, forEachVars);
        assertThat(result).isEqualTo("${each.instrument.symbol}");
    }
}
