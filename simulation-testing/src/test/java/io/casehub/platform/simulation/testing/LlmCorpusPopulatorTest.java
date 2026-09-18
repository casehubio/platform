package io.casehub.platform.simulation.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.simulation.CorpusSeed;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmCorpusPopulatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void populateParsesJsonArrayAndAddsToSeed() {
        var promptCapture = new AtomicReference<String>();
        var populator = new LlmCorpusPopulator(
                prompt -> {
                    promptCapture.set(prompt);
                    return "[{\"input\": \"hello\", \"output\": 42}, {\"input\": \"world\", \"output\": 99}]";
                },
                objectMapper);

        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        populator.populate(seed, String.class, Integer.class, 2, "Test context");

        assertThat(seed.build()).hasSize(2);
        assertThat(seed.build().get(0).input()).isEqualTo("hello");
        assertThat(seed.build().get(0).output()).isEqualTo(42);
    }

    @Test
    void promptContainsSchemaAndContext() {
        var promptCapture = new AtomicReference<String>();
        var populator = new LlmCorpusPopulator(
                prompt -> {
                    promptCapture.set(prompt);
                    return "[]";
                },
                objectMapper);

        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        populator.populate(seed, String.class, Integer.class, 5, "Healthcare domain");

        assertThat(promptCapture.get()).contains("Healthcare domain");
        assertThat(promptCapture.get()).contains("5");
        assertThat(promptCapture.get()).contains("type");
    }

    @Test
    void existingEntriesIncludedAsExamples() {
        var promptCapture = new AtomicReference<String>();
        var populator = new LlmCorpusPopulator(
                prompt -> {
                    promptCapture.set(prompt);
                    return "[]";
                },
                objectMapper);

        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        seed.add("example", 1);
        populator.populate(seed, String.class, Integer.class, 5, "Context");

        assertThat(promptCapture.get()).contains("example");
    }

    @Test
    void outputAdapterTransformsRawOutput() {
        var populator = new LlmCorpusPopulator(
                prompt -> "[{\"input\": \"hello\", \"output\": 42}]",
                objectMapper);

        var seed = new CorpusSeed<String, String>("spi.method", "tenant-1");
        populator.populate(seed, String.class, Integer.class,
                i -> "wrapped:" + i, 1, "Context");

        assertThat(seed.build().get(0).output()).isEqualTo("wrapped:42");
    }

    @Test
    void invalidJsonThrowsUnchecked() {
        var populator = new LlmCorpusPopulator(
                prompt -> "not valid json",
                objectMapper);

        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");

        assertThatThrownBy(() ->
                populator.populate(seed, String.class, Integer.class, 1, "Context"))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }
}
