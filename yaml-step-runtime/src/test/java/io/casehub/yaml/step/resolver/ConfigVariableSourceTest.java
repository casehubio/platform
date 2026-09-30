package io.casehub.yaml.step.resolver;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.UnresolvedVariableException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigVariableSourceTest {

    private final Map<String, String> properties = Map.of(
            "app.name", "casehub",
            "app.timeout-ms", "5000",
            "fsitrading.fix-session-id", "FIX.4.4:SENDER->TARGET"
    );

    private final ConfigVariableSource source = new ConfigVariableSource(properties::get);

    @Test
    void resolvesKnownProperty() {
        assertThat(source.resolve("app.name")).isEqualTo("casehub");
    }

    @Test
    void returnsNullForUnknownProperty() {
        assertThat(source.resolve("no.such.key")).isNull();
    }

    @Test
    void resolvesPropertyWithSpecialCharacters() {
        assertThat(source.resolve("fsitrading.fix-session-id"))
                .isEqualTo("FIX.4.4:SENDER->TARGET");
    }

    @Test
    void integrationWithVariableResolver() {
        var resolver = new VariableResolver(
                Map.of("config", source), Set.of());
        assertThat(resolver.resolveString("${config.app.name}", "test"))
                .isEqualTo("casehub");
    }

    @Test
    void resolverEmbeddedInString() {
        var resolver = new VariableResolver(
                Map.of("config", source), Set.of());
        assertThat(resolver.resolveString("timeout=${config.app.timeout-ms}ms", "test"))
                .isEqualTo("timeout=5000ms");
    }

    @Test
    void resolverThrowsForUnknownConfigKey() {
        var resolver = new VariableResolver(
                Map.of("config", source), Set.of());
        assertThatThrownBy(() -> resolver.resolveString("${config.missing.key}", "test"))
                .isInstanceOf(UnresolvedVariableException.class);
    }
}
