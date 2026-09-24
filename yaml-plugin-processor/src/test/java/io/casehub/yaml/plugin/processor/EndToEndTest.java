package io.casehub.yaml.plugin.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

class EndToEndTest {

    @Test
    void assertPluginGeneratesAction() {
        Compilation compilation = javac()
            .withProcessors(new StepPluginProcessor())
            .compile(JavaFileObjects.forResource("test-plugins/AssertPlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation).generatedSourceFile("test.plugins.AssertPluginAction");
    }

    @Test
    void assertPluginSchemaHasRequiredCondition() throws IOException {
        Compilation compilation = javac()
            .withProcessors(new StepPluginProcessor())
            .compile(JavaFileObjects.forResource("test-plugins/AssertPlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();

        JavaFileObject schema = compilation.generatedFile(
            StandardLocation.CLASS_OUTPUT,
            "META-INF/yaml-plugins/assert.schema.json").orElseThrow();
        String content = schema.getCharContent(false).toString();

        assertThat(content).contains("\"condition\"");
        assertThat(content).contains("\"required\": [\"condition\"]");
        assertThat(content).contains("\"type\": \"string\"");
    }

    @Test
    void assertPluginGeneratedActionValidatesRequired() throws IOException {
        Compilation compilation = javac()
            .withProcessors(new StepPluginProcessor())
            .compile(JavaFileObjects.forResource("test-plugins/AssertPlugin.java"));

        JavaFileObject source = compilation.generatedSourceFile(
            "test.plugins.AssertPluginAction").orElseThrow();
        String content = source.getCharContent(false).toString();

        assertThat(content).contains("assert: 'condition' is required");
    }

    @Test
    void assertPluginRegistryManifest() throws IOException {
        Compilation compilation = javac()
            .withProcessors(new StepPluginProcessor())
            .compile(JavaFileObjects.forResource("test-plugins/AssertPlugin.java"));

        JavaFileObject manifest = compilation.generatedFile(
            StandardLocation.CLASS_OUTPUT,
            "META-INF/yaml-plugins/assert.json").orElseThrow();
        String content = manifest.getCharContent(false).toString();

        assertThat(content).contains("\"name\": \"assert\"");
        assertThat(content).contains("\"actionClass\": \"test.plugins.AssertPluginAction\"");
    }

    @Test
    void compareStatePluginGeneratesAction() {
        Compilation compilation = javac()
            .withProcessors(new StepPluginProcessor())
            .compile(JavaFileObjects.forResource("test-plugins/CompareStatePlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation).generatedSourceFile("test.plugins.CompareStatePluginAction");
    }

    @Test
    void compareStatePluginSchemaHasNoRequiredFields() throws IOException {
        Compilation compilation = javac()
            .withProcessors(new StepPluginProcessor())
            .compile(JavaFileObjects.forResource("test-plugins/CompareStatePlugin.java"));

        JavaFileObject schema = compilation.generatedFile(
            StandardLocation.CLASS_OUTPUT,
            "META-INF/yaml-plugins/compare-state.schema.json").orElseThrow();
        String content = schema.getCharContent(false).toString();

        assertThat(content).contains("\"absent-when\"");
        assertThat(content).contains("\"drifted-when\"");
        assertThat(content).contains("\"present-when\"");
        assertThat(content).contains("\"required\": []");
    }

    @Test
    void compareStatePluginRegistryManifest() throws IOException {
        Compilation compilation = javac()
            .withProcessors(new StepPluginProcessor())
            .compile(JavaFileObjects.forResource("test-plugins/CompareStatePlugin.java"));

        JavaFileObject manifest = compilation.generatedFile(
            StandardLocation.CLASS_OUTPUT,
            "META-INF/yaml-plugins/compare-state.json").orElseThrow();
        String content = manifest.getCharContent(false).toString();

        assertThat(content).contains("\"name\": \"compare-state\"");
        assertThat(content).contains("\"actionClass\": \"test.plugins.CompareStatePluginAction\"");
    }
}
