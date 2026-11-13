package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.PluginRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JandexPluginSourceTest {

    @Test
    void discovers_plugins_from_jandex_index() {
        var registry = new CompositePluginRegistry();
        var source = new JandexPluginSource();
        source.populate(registry);

        assertThat(registry.resolve("assert")).isPresent();
        assertThat(registry.resolve("rest-call")).isPresent();
    }

    @Test
    void does_not_fail_when_no_jandex_indexes() {
        var registry = new CompositePluginRegistry();
        var source = new JandexPluginSource();
        source.populate(registry);
    }
}
