package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulateBlockParserTest {

    @Test
    void agentAliasResolvesToQualifiedName() {
        var block = Map.<String, Object>of("agent", List.of(
                Map.of("prompt", "hello", "response", "world")
        ));

        var parsed = SimulateBlockParser.parse(block);
        assertThat(parsed.config().strategyFor("agent-provider.invoke")).isPresent();
        assertThat(parsed.config().strategyFor("agent-provider.invoke").get()).isEqualTo("key");
    }

    @Test
    void promptResponseNormalisedToKeyOutput() {
        var block = Map.<String, Object>of("agent", List.of(
                Map.of("prompt", "hello", "response", "world")
        ));

        var parsed = SimulateBlockParser.parse(block);
        var records = parsed.corpus().list("agent-provider.invoke");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo("hello");
        assertThat(records.get(0).output()).isEqualTo("world");
    }

    @Test
    void multipleCorpusEntries() {
        var block = Map.<String, Object>of("agent", List.of(
                Map.of("prompt", "risk", "response", "moderate"),
                Map.of("prompt", "rebalance", "response", "shift bonds")
        ));

        var parsed = SimulateBlockParser.parse(block);
        assertThat(parsed.corpus().list("agent-provider.invoke")).hasSize(2);
    }

    @Test
    void fullyQualifiedNamePassedThrough() {
        var block = Map.<String, Object>of("portfolio-api.getPortfolio", Map.of(
                "strategy", "key",
                "corpus", List.of(
                        Map.of("key", "/api/portfolio", "output", Map.of("total", 150000))
                )
        ));

        var parsed = SimulateBlockParser.parse(block);
        assertThat(parsed.config().strategyFor("portfolio-api.getPortfolio")).isPresent();
        assertThat(parsed.corpus().list("portfolio-api.getPortfolio")).hasSize(1);
    }

    @Test
    void customStrategyOverridesDefault() {
        var block = Map.<String, Object>of("agent", Map.of(
                "strategy", "nearest-match",
                "corpus", List.of(
                        Map.of("prompt", "test", "response", "answer")
                )
        ));

        var parsed = SimulateBlockParser.parse(block);
        assertThat(parsed.config().strategyFor("agent-provider.invoke").get())
                .isEqualTo("nearest-match");
    }

    @Test
    void corpusFileLoadedFromDisk() {
        var block = Map.<String, Object>of("agent", Map.of(
                "corpus-file", "corpora/test-corpus.yaml"
        ));

        var parsed = SimulateBlockParser.parse(block,
                Path.of("src/test/resources"));
        assertThat(parsed.corpus().list("agent-provider.invoke")).isNotEmpty();
    }

    @Test
    void mixedCorpusFileAndInlineEntries() {
        var block = Map.<String, Object>of("agent", Map.of(
                "corpus-file", "corpora/test-corpus.yaml",
                "corpus", List.of(
                        Map.of("prompt", "extra", "response", "bonus")
                )
        ));

        var parsed = SimulateBlockParser.parse(block,
                Path.of("src/test/resources"));
        var records = parsed.corpus().list("agent-provider.invoke");
        assertThat(records.size()).isGreaterThanOrEqualTo(2);
    }
}
