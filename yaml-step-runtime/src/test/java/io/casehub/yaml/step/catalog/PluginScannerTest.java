package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.scannertest.CamelCasePlugin;
import io.casehub.yaml.step.catalog.scannertest.TestPlugin;
import io.casehub.yaml.step.catalog.scannertest.UniversalPlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PluginScannerTest {

    @Test
    void scansRecordComponentsIntoParameters() {
        PluginScanner scanner = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();

        scanner.scanAndRegister(TestPlugin.class, registry);

        var def = registry.resolve("test-plugin");
        assertThat(def).isPresent();
        assertThat(def.get().description()).isEqualTo("A test plugin");
        assertThat(def.get().inputs()).hasSize(2);
        assertThat(def.get().inputs().get("name").required()).isTrue();
        assertThat(def.get().inputs().get("name").type()).isEqualTo(ParameterType.STRING);
        assertThat(def.get().inputs().get("count").required()).isTrue();
        assertThat(def.get().inputs().get("count").type()).isEqualTo(ParameterType.INTEGER);
        assertThat(def.get().portability()).isEqualTo(Portability.JAVA);
    }

    @Test
    void executesWrappedAction() {
        PluginScanner scanner = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();
        scanner.scanAndRegister(TestPlugin.class, registry);

        var def = registry.resolve("test-plugin").orElseThrow();
        Result result = def.action().execute(
                Map.of("name", "World", "count", 3),
                new MapServiceRegistry());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("greeting", "Hello World");
    }

    @Test
    void usesKebabCaseKeys() {
        PluginScanner scanner = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();

        scanner.scanAndRegister(CamelCasePlugin.class, registry);
        var def = registry.resolve("camel-test").orElseThrow();
        assertThat(def.inputs()).containsKey("first-name");
        assertThat(def.inputs()).containsKey("max-retries");
    }

    @Test
    void respectsPortabilityAnnotation() {
        PluginScanner scanner = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();
        scanner.scanAndRegister(UniversalPlugin.class, registry);

        assertThat(registry.resolve("universal-plugin").get().portability())
                .isEqualTo(Portability.UNIVERSAL);
    }
}
