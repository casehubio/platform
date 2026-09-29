package io.casehub.platform.expression;

import io.casehub.platform.api.expression.ConfigMapNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ConfigManagerCoreTest {

    private ConfigManagerCore managerWith(Map<String, String> props) {
        return new ConfigManagerCore(new MapPropertySource(props));
    }

    @Test
    void config_returns_string_value() {
        var mgr = managerWith(Map.of("app.name", "test"));
        assertThat(mgr.config("app.name", String.class)).hasValue("test");
    }

    @Test
    void config_returns_empty_when_missing() {
        var mgr = managerWith(Map.of());
        assertThat(mgr.config("missing", String.class)).isEmpty();
    }

    @Test
    void config_converts_integer() {
        var mgr = managerWith(Map.of("app.port", "8080"));
        assertThat(mgr.config("app.port", Integer.class)).hasValue(8080);
    }

    @Test
    void config_converts_boolean() {
        var mgr = managerWith(Map.of("app.debug", "true"));
        assertThat(mgr.config("app.debug", Boolean.class)).hasValue(true);
    }

    @Test
    void multiConfig_splits_comma_separated() {
        var mgr = managerWith(Map.of("app.tags", "a, b, c"));
        assertThat(mgr.multiConfig("app.tags", String.class))
                .containsExactly("a", "b", "c");
    }

    @Test
    void multiConfig_returns_empty_when_missing() {
        var mgr = managerWith(Map.of());
        assertThat(mgr.multiConfig("missing", String.class)).isEmpty();
    }

    @Test
    void names_returns_all_property_names() {
        var mgr = managerWith(Map.of("a", "1", "b", "2"));
        assertThat(mgr.names()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void configMap_builds_nested_map() {
        var mgr = managerWith(Map.of(
                "myapp.timeout", "5000",
                "myapp.database.host", "localhost",
                "myapp.database.port", "5432",
                "other.key", "ignored"));
        Map<String, Object> result = mgr.configMap("myapp");
        assertThat(result).containsEntry("timeout", "5000");
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) result.get("database");
        assertThat(db).containsEntry("host", "localhost").containsEntry("port", "5432");
    }

    @Test
    void configMap_throws_when_no_properties_match() {
        var mgr = managerWith(Map.of("other.key", "value"));
        assertThatThrownBy(() -> mgr.configMap("missing"))
                .isInstanceOf(ConfigMapNotFoundException.class);
    }

    static class MapPropertySource implements PropertySource {
        private final Map<String, String> props;
        MapPropertySource(Map<String, String> props) { this.props = props; }
        @Override public Optional<String> getProperty(String name) {
            return Optional.ofNullable(props.get(name));
        }
        @Override public Iterable<String> getPropertyNames() { return props.keySet(); }
    }
}
