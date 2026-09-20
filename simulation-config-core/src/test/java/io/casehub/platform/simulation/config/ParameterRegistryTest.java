package io.casehub.platform.simulation.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterRegistryTest {

    @Test
    void parseSingleParamMethod() {
        var registry = ParameterRegistry.parse(props("spi.balance=accountId:0"));

        var params = registry.paramsFor("spi.balance");
        assertThat(params).isPresent();
        assertThat(params.get().totalParams()).isEqualTo(1);
        assertThat(params.get().positionOf("accountId")).hasValue(0);
    }

    @Test
    void parseMultiParamMethod() {
        var registry = ParameterRegistry.parse(props(
                "spi.transfer=fromAccount:0,toAccount:1,amount:2"));

        var params = registry.paramsFor("spi.transfer");
        assertThat(params).isPresent();
        assertThat(params.get().totalParams()).isEqualTo(3);
        assertThat(params.get().positionOf("fromAccount")).hasValue(0);
        assertThat(params.get().positionOf("toAccount")).hasValue(1);
        assertThat(params.get().positionOf("amount")).hasValue(2);
    }

    @Test
    void parseZeroArgMethod() {
        var registry = ParameterRegistry.parse(props("spi.count="));

        var params = registry.paramsFor("spi.count");
        assertThat(params).isPresent();
        assertThat(params.get().totalParams()).isEqualTo(0);
    }

    @Test
    void unknownMethodReturnsEmpty() {
        var registry = ParameterRegistry.parse(props("spi.balance=accountId:0"));
        assertThat(registry.paramsFor("spi.unknown")).isEmpty();
    }

    @Test
    void emptyRegistryReturnsEmpty() {
        var registry = ParameterRegistry.parse(new Properties());
        assertThat(registry.paramsFor("spi.anything")).isEmpty();
    }

    private static Properties props(String content) {
        var properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return properties;
    }
}
