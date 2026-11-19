package io.casehub.yaml.plugin.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.List;

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

    @Test
    void listStringFieldGeneratesArrayWithStringItems() throws IOException {
        Compilation compilation = javac()
                                          .withProcessors(new StepPluginProcessor())
                                          .compile(JavaFileObjects.forResource("test-plugins/ListTestPlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();

        JavaFileObject schema = compilation.generatedFile(
                StandardLocation.CLASS_OUTPUT,
                "META-INF/yaml-plugins/list-test.schema.json").orElseThrow();
        String content = schema.getCharContent(false).toString();

        assertThat(content).contains("\"tags\"");
        assertThat(content).contains("\"items\": { \"type\": \"string\" }");
    }

    @Test
    void listRecordFieldGeneratesArrayWithObjectItems() throws IOException {
        Compilation compilation = javac()
                                          .withProcessors(new StepPluginProcessor())
                                          .compile(JavaFileObjects.forResource("test-plugins/ListTestPlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();

        JavaFileObject schema = compilation.generatedFile(
                StandardLocation.CLASS_OUTPUT,
                "META-INF/yaml-plugins/list-test.schema.json").orElseThrow();
        String content = schema.getCharContent(false).toString();

        assertThat(content).contains("\"marks\"");
        assertThat(content).contains("\"type\": \"array\"");
        assertThat(content).contains("\"items\"");
        assertThat(content).contains("\"type\": \"object\"");
        assertThat(content).contains("\"id\": { \"type\": \"string\" }");
        assertThat(content).contains("\"char-offset\": { \"type\": \"integer\" }");
        assertThat(content).contains("\"required\": [\"id\", \"char-offset\"]");
    }

    @Test
    void nestedRecordListFieldGeneratesArrayItemsRecursively() throws IOException {
        Compilation compilation = javac()
                                          .withProcessors(new StepPluginProcessor())
                                          .compile(JavaFileObjects.forResource("test-plugins/NestedListTestPlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();

        JavaFileObject schema = compilation.generatedFile(
                StandardLocation.CLASS_OUTPUT,
                "META-INF/yaml-plugins/nested-list-test.schema.json").orElseThrow();
        String content = schema.getCharContent(false).toString();

        assertThat(content).contains("\"tags\":");
        assertThat(content).contains("\"type\": \"array\"");
        assertThat(content).contains("\"items\": { \"type\": \"string\" }");
    }

    @Test
    void standaloneRecordFieldGeneratesObjectWithProperties() throws IOException {
        Compilation compilation = javac()
                                          .withProcessors(new StepPluginProcessor())
                                          .compile(JavaFileObjects.forResource("test-plugins/RecordFieldTestPlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();

        JavaFileObject schema = compilation.generatedFile(
                StandardLocation.CLASS_OUTPUT,
                "META-INF/yaml-plugins/record-field-test.schema.json").orElseThrow();
        String content = schema.getCharContent(false).toString();

        assertThat(content).contains("\"address\"");
        assertThat(content).contains("\"street\": { \"type\": \"string\" }");
        assertThat(content).contains("\"city\": { \"type\": \"string\" }");
        assertThat(content).contains("\"post-code\": { \"type\": \"integer\" }");
        assertThat(content).contains("\"required\": [\"street\", \"city\", \"post-code\"]");
    }

    @Test
    void allSchemaTestPluginsGenerateValidJson() throws IOException {
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String plugin : List.of("ListTestPlugin", "NestedListTestPlugin", "RecordFieldTestPlugin")) {
            Compilation compilation = javac()
                                              .withProcessors(new StepPluginProcessor())
                                              .compile(JavaFileObjects.forResource("test-plugins/" + plugin + ".java"));
            assertThat(compilation).succeededWithoutWarnings();

            for (var generatedFile : compilation.generatedFiles()) {
                if (generatedFile.getName().endsWith(".schema.json")) {
                    String content = generatedFile.getCharContent(false).toString();
                    var    parsed  = objectMapper.readTree(content);
                    assertThat(parsed.has("$schema")).as("schema in " + plugin).isTrue();
                    assertThat(parsed.has("properties")).as("properties in " + plugin).isTrue();
                }
            }
        }
    }

    @Test
    void listOfListGeneratesNestedArrayItems() throws IOException {
        Compilation compilation = javac()
                                          .withProcessors(new StepPluginProcessor())
                                          .compile(JavaFileObjects.forResource("test-plugins/NestedListOfListTestPlugin.java"));
        assertThat(compilation).succeededWithoutWarnings();

        JavaFileObject schema = compilation.generatedFile(
                StandardLocation.CLASS_OUTPUT,
                "META-INF/yaml-plugins/nested-list-of-list-test.schema.json").orElseThrow();
        String content = schema.getCharContent(false).toString();

        assertThat(content).contains("\"matrix\"");
        assertThat(content).contains("\"type\": \"array\"");
        assertThat(content).contains("\"type\": \"array\"");
        assertThat(content).contains("\"items\": { \"type\": \"string\" }");
    }


}
