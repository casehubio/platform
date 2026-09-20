package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationConfigException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

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

    @Test
    void bareParamNameSingleArgResolvesToIdentity() {
        var registry = ParameterRegistry.parse(props("spi.balance=accountId:0"));
        var registryFactory = new DeclarativeExtractorFactory(registry);

        KeyExtractor<Object> extractor = registryFactory.create("accountId", "spi.balance");
        assertThat(extractor.extract("acct-123")).isEqualTo("acct-123");
    }

    @Test
    void bareParamNameMultiArgResolvesToPositional() {
        var registry = ParameterRegistry.parse(props(
                "spi.transfer=fromAccount:0,toAccount:1,amount:2"));
        var registryFactory = new DeclarativeExtractorFactory(registry);

        KeyExtractor<Object> extractor = registryFactory.create("toAccount", "spi.transfer");
        Object[] args = {"acct-a", "acct-b", java.math.BigDecimal.valueOf(500)};
        assertThat(extractor.extract(args)).isEqualTo("acct-b");
    }

    @Test
    void unknownParamNameThrowsWithValidNames() {
        var registry = ParameterRegistry.parse(props("spi.balance=accountId:0"));
        var registryFactory = new DeclarativeExtractorFactory(registry);

        assertThatThrownBy(() -> registryFactory.create("bogusParam", "spi.balance"))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("bogusParam")
                .hasMessageContaining("accountId");
    }

    @Test
    void existingSpecsStillWorkWithRegistryConstructor() {
        var registry = ParameterRegistry.parse(props("spi.balance=accountId:0"));
        var registryFactory = new DeclarativeExtractorFactory(registry);

        assertThat(registryFactory.create("identity", "spi.balance").extract("hello"))
                .isEqualTo("hello");
        assertThat(registryFactory.create("field:domain", "spi.query")
                .extract(Map.of("domain", "cardiology")))
                .isEqualTo("cardiology");
    }

    private static Properties props(String content) {
        var properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(
                    content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return properties;
    }
}
