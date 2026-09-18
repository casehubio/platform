package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationConfigException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclarativeExtractorFactoryTest {

    private final DeclarativeExtractorFactory factory = new DeclarativeExtractorFactory();

    @Test
    void identityExtractorUsesToString() {
        KeyExtractor<Object> extractor = factory.create("identity");

        assertThat(extractor.extract("hello")).isEqualTo("hello");
        assertThat(extractor.extract(42)).isEqualTo("42");
    }

    @Test
    void fieldExtractorFromMap() {
        KeyExtractor<Object> extractor = factory.create("field:domain");

        String key = extractor.extract(Map.of("domain", "cardiology", "other", "x"));
        assertThat(key).isEqualTo("cardiology");
    }

    @Test
    void fieldExtractorFromRecord() {
        KeyExtractor<Object> extractor = factory.create("field:name");

        record TestInput(String name, int age) {}
        String key = extractor.extract(new TestInput("alice", 30));
        assertThat(key).isEqualTo("alice");
    }

    @Test
    void compositeExtractorConcatenatesFields() {
        KeyExtractor<Object> extractor = factory.create("composite:department,severity");

        String key = extractor.extract(Map.of("department", "ER", "severity", "HIGH"));
        assertThat(key).isEqualTo("department=ER:severity=HIGH");
    }

    @Test
    void unknownSpecThrowsConfigException() {
        assertThatThrownBy(() -> factory.create("bogus"))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("Unknown key-extractor spec");
    }

    @Test
    void fieldExtractorMissingFieldReturnsNull() {
        KeyExtractor<Object> extractor = factory.create("field:missing");

        String key = extractor.extract(Map.of("other", "value"));
        assertThat(key).isEqualTo("null");
    }
}
