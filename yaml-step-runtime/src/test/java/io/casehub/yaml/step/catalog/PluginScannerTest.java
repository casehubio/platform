package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.scannertest.CamelCasePlugin;
import io.casehub.yaml.step.catalog.scannertest.TestPlugin;
import io.casehub.yaml.step.catalog.scannertest.UniversalPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void propagatesAbsorbSiblingsFromAnnotation() {
        PluginScanner           scanner  = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();
        scanner.scanAndRegister(io.casehub.yaml.step.catalog.scannertest.AbsorbingPlugin.class, registry);

        var def = registry.resolve("absorbing-plugin").orElseThrow();
        assertThat(def.absorbSiblings()).isTrue();
    }

    @Test
    void absorbSiblingsDefaultsToFalse() {
        PluginScanner           scanner  = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();
        scanner.scanAndRegister(TestPlugin.class, registry);

        var def = registry.resolve("test-plugin").orElseThrow();
        assertThat(def.absorbSiblings()).isFalse();
    }

    @Test
    void streamingPluginResolvesFirstExecParamFromStepInput() {
        PluginScanner           scanner  = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();
        scanner.scanAndRegister(io.casehub.yaml.step.catalog.scannertest.StreamingPlugin.class, registry);

        var def = registry.resolve("streaming-test").orElseThrow();
        Result result = def.action().execute(
                Map.of("text", "hello"),
                new MapServiceRegistry());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("_output", "hello:hello");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_value", "audio"})
    void streamingPluginWithAllOptionalInputsAcceptsScalarAndNamedInput(String inputKey) {
        PluginScanner           scanner  = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();
        scanner.scanAndRegister(io.casehub.yaml.step.catalog.scannertest.AllOptionalStreamingPlugin.class, registry);

        var def = registry.resolve("all-optional-streaming").orElseThrow();
        Result result = def.action().execute(
                Map.of(inputKey, "test-data", "language", "fr"),
                new MapServiceRegistry());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("_output", "fr:test-data");
    }

    @Test
    void streamingRecordReturnIsUnwrappedToMap() {
        PluginScanner           scanner  = new PluginScanner();
        CompositePluginRegistry registry = new CompositePluginRegistry();
        scanner.scanAndRegister(io.casehub.yaml.step.catalog.scannertest.RecordOutputStreamingPlugin.class, registry);

        var def = registry.resolve("record-output-streaming").orElseThrow();
        Result result = def.action().execute(
                Map.of("_value", "hello world", "language", "fr"),
                new MapServiceRegistry());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("text", "hello world");
        assertThat(result.output()).containsEntry("detected-language", "fr");
        assertThat(result.output()).containsEntry("confidence", 0.95);
    }

    @Test
    void aptGeneratedMaterializeUnwrapsRecordOutputToKebabCaseMap() {
        var    step   = new io.casehub.yaml.step.catalog.scannertest.RecordOutputStreamingPluginStreamingStep("fr");
        Result result = step.materialize(Map.of("audio", "hello world"), new MapServiceRegistry());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("text", "hello world");
        assertThat(result.output()).containsEntry("detected-language", "fr");
        assertThat(result.output()).containsEntry("confidence", 0.95);
        assertThat(result.output()).doesNotContainKey("output");
    }


}
