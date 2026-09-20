package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.SimulationConfigException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeCorpusLoaderTest {

    @Test
    void dispatchesByExtension() {
        var composite = new CompositeCorpusLoader(
                new YamlCorpusLoader(), new CsvCorpusLoader(), new JsonCorpusLoader());

        assertThat(composite.supports("data.yaml")).isTrue();
        assertThat(composite.supports("data.yml")).isTrue();
        assertThat(composite.supports("data.csv")).isTrue();
        assertThat(composite.supports("data.json")).isTrue();
        assertThat(composite.supports("data.txt")).isFalse();
    }

    @Test
    void unsupportedExtensionThrows() {
        var composite = new CompositeCorpusLoader(new YamlCorpusLoader());

        assertThatThrownBy(() -> composite.loadFromPaths(
                List.of("classpath:data.txt"), "t1"))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("No corpus loader");
    }
}
