package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookSchemaValidationTest {

    private static JsonNode schemaNode;
    private static JsonSchema schema;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void loadSchema() throws IOException {
        try (InputStream is = PlaybookSchemaValidationTest.class
                .getClassLoader().getResourceAsStream("schema/playbook-document.schema.json")) {
            assertThat(is).as("Schema must be on classpath").isNotNull();
            schemaNode = MAPPER.readTree(is);
        }
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(schemaNode);
    }

    @Test
    void allStructuralKeysPresent() {
        JsonNode stepProps = schemaNode.at("/$defs/step/properties");
        assertThat(stepProps.isMissingNode())
                  .as("$defs/step/properties must exist")
                  .isFalse();
        Set<String> schemaKeys = new HashSet<>();
        stepProps.fieldNames().forEachRemaining(schemaKeys::add);

        for (String key : StepKeywords.STRUCTURAL_KEYS) {
            assertThat(schemaKeys)
                      .as("Structural key '%s' must appear in $defs/step/properties", key)
                      .contains(key);
        }
    }

    @Test
    void allDecoratorKeysPresent() {
        JsonNode stepProps = schemaNode.at("/$defs/step/properties");
        assertThat(stepProps.isMissingNode())
                  .as("$defs/step/properties must exist")
                  .isFalse();
        for (String key : StepKeywords.DECORATOR_KEYS) {
            assertThat(stepProps.has(key))
                      .as("Decorator key '%s' must appear in $defs/step/properties", key)
                      .isTrue();
        }
    }

    @Test
    void allPluginSchemasPresent() throws IOException {
        Set<String> pluginNames = new HashSet<>();
        var classLoader = getClass().getClassLoader();
        var resources = classLoader.getResources("META-INF/yaml-plugins");
        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            try {
                Path dir = Path.of(url.toURI());
                if (Files.isDirectory(dir)) {
                    try (Stream<Path> stream = Files.list(dir)) {
                        stream.filter(p -> p.toString().endsWith(".schema.json"))
                                .forEach(p -> {
                                    String name = p.getFileName().toString()
                                            .replace(".schema.json", "");
                                    pluginNames.add(name);
                                });
                    }
                }
            } catch (Exception e) {
                // jar: URLs — skip
            }
        }

        Set<String> testOnlyPlugins = Set.of("test-plugin", "camel-test", "universal-plugin", "absorbing-plugin");
        pluginNames.removeAll(testOnlyPlugins);
        assertThat(pluginNames).as("Should find production plugin schemas on classpath").isNotEmpty();

        JsonNode stepProps = schemaNode.at("/$defs/step/properties");
        for (String plugin : pluginNames) {
            assertThat(stepProps.has(plugin))
                      .as("Plugin '%s' must have a property in $defs/step", plugin)
                      .isTrue();
        }
    }

    @Test
    void allCombinedKeyPrefixesPresent() {
        JsonNode patternProps = schemaNode.at("/$defs/step/patternProperties");
        assertThat(patternProps.isMissingNode())
                  .as("$defs/step/patternProperties must exist")
                  .isFalse();

        Set<String> patterns = new HashSet<>();
        patternProps.fieldNames().forEachRemaining(patterns::add);

        Set<String> prefixes = Set.of(
                "forEach", "retry", "timeout", "when", "mutex", "loop",
                "block", "parallel", "try", "select", "race",
                "if", "match", "barrier", "quorum");
        for (String prefix : prefixes) {
            boolean found = patterns.stream()
                    .anyMatch(p -> p.startsWith("^" + prefix + " "));
            assertThat(found)
                      .as("Combined-key prefix '%s' must have a patternProperties entry", prefix)
                      .isTrue();
        }
    }

    @TestFactory
    Collection<DynamicTest> playbookFixturesValidateAgainstSchema() throws IOException {
        Path playbooksDir = findPlaybooksDir();
        Yaml snakeYaml = new Yaml();

        List<Path> yamlFiles;
        try (Stream<Path> stream = Files.walk(playbooksDir)) {
            yamlFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        }

        List<DynamicTest> tests = yamlFiles.stream()
                .map(path -> DynamicTest.dynamicTest(
                        "playbook: " + playbooksDir.relativize(path),
                        () -> validatePlaybookFixture(path, snakeYaml)))
                .toList();

        assertThat(tests).isNotEmpty();
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> testFixturesValidateAgainstSchema() throws IOException {
        List<Path> testFiles = new ArrayList<>();
        for (String dir : List.of(
                "../yaml-test-core/src/test/resources/test-suite",
                "../yaml-test/src/test/resources/tutorials",
                "../yaml-test/src/test/resources/discovery-suite")) {
            Path dirPath = Path.of(dir);
            if (Files.isDirectory(dirPath)) {
                try (Stream<Path> stream = Files.walk(dirPath)) {
                    stream.filter(p -> p.toString().endsWith(".test.yaml"))
                            .forEach(testFiles::add);
                }
            }
        }

        if (testFiles.isEmpty()) {
            return List.of();
        }

        return testFiles.stream()
                .map(path -> DynamicTest.dynamicTest(
                        "test: " + path.getFileName(),
                        () -> validateTestFixture(path)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void validatePlaybookFixture(Path yamlPath, Yaml snakeYaml) throws IOException {
        List<Object> documents = new ArrayList<>();
        try (InputStream is = Files.newInputStream(yamlPath)) {
            for (Object doc : snakeYaml.loadAll(is)) {
                if (doc != null) documents.add(doc);
            }
        }

        Object contentDoc = null;
        for (Object doc : documents) {
            if (doc instanceof Map<?, ?> map && !map.containsKey("playbook")) {
                contentDoc = doc;
                break;
            }
        }
        if (contentDoc == null && documents.size() >= 2) {
            contentDoc = documents.get(1);
        }
        if (contentDoc == null || contentDoc instanceof List<?>) {
            return;
        }

        JsonNode jsonContent = MAPPER.valueToTree(contentDoc);
        Set<ValidationMessage> errors = schema.validate(jsonContent);

        assertThat(errors)
                  .as("Fixture %s should validate against schema: %s",
                        yamlPath.getFileName(),
                        errors.stream().map(ValidationMessage::getMessage)
                                .collect(Collectors.joining("; ")))
                  .isEmpty();
    }

    private void validateTestFixture(Path yamlPath) throws IOException {
        Yaml snakeYaml = new Yaml();
        Object doc;
        try (InputStream is = Files.newInputStream(yamlPath)) {
            doc = snakeYaml.load(is);
        }
        if (doc == null) return;

        JsonNode jsonContent = MAPPER.valueToTree(doc);
        Set<ValidationMessage> errors = schema.validate(jsonContent);

        assertThat(errors)
                  .as("Test file %s should validate: %s",
                        yamlPath.getFileName(),
                        errors.stream().map(ValidationMessage::getMessage)
                                .collect(Collectors.joining("; ")))
                  .isEmpty();
    }

    private static Path findPlaybooksDir() {
        Path candidate = Path.of("../playbooks/src/main/playbooks");
        if (Files.isDirectory(candidate)) return candidate;
        throw new AssertionError("Cannot find playbooks directory at " + candidate.toAbsolutePath());
    }
}
