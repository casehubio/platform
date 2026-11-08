package io.casehub.yaml.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class YamlTestDiscovery {

    private static final String TEST_SUFFIX = ".test.yaml";
    private static final String LIFECYCLE_PREFIX = "_";
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    public static final  Path         DEFAULT_TEST_ROOT = Path.of("src/test/playbooks");


    private YamlTestDiscovery() {}

    public static List<TestFileDescriptor> discover(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> testFiles = findTestFiles(root);
        List<TestFileDescriptor> descriptors = new ArrayList<>(testFiles.size());
        for (Path file : testFiles) {
            descriptors.add(parseTestFile(file));
        }
        return descriptors;
    }

    private static List<Path> findTestFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(TEST_SUFFIX))
                    .filter(p -> !p.getFileName().toString().startsWith(LIFECYCLE_PREFIX))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to scan test directory: " + root, e);
        }
    }

    @SuppressWarnings("unchecked")
    static TestFileDescriptor parseTestFile(Path file) {
        try {
            Map<String, Object>       doc           = MAPPER.readValue(Files.newInputStream(file), Map.class);
            String                    name          = (String) doc.getOrDefault("name", file.getFileName().toString());
            Map<String, Object>       simulateBlock = (Map<String, Object>) doc.get("simulate");
            List<Map<String, Object>> fileSetup     = (List<Map<String, Object>>) doc.get("setup");
            List<Map<String, Object>> fileTeardown  = (List<Map<String, Object>>) doc.get("teardown");
            Map<String, Object>       varsBlock     = (Map<String, Object>) doc.get("vars");
            Map<String, Object>       paramsBlock   = (Map<String, Object>) doc.get("params");

            List<TestCase> testCases;
            if (doc.containsKey("tests")) {
                var tests = (List<Map<String, Object>>) doc.get("tests");
                testCases = new ArrayList<>(tests.size());
                for (var testEntry : tests) {
                    String testName        = (String) testEntry.get("name");
                    var    perTestSetup    = (List<Map<String, Object>>) testEntry.get("setup");
                    var    steps           = (List<Map<String, Object>>) testEntry.get("steps");
                    var    perTestTeardown = (List<Map<String, Object>>) testEntry.get("teardown");
                    var perTestSimulate = (Map<String, Object>) testEntry.get("simulate");
                    testCases.add(new TestCase(testName, perTestSetup, steps, perTestTeardown, perTestSimulate));
                }
            } else {
                var steps = (List<Map<String, Object>>) doc.get("steps");
                testCases = List.of(new TestCase(name, null, steps, null));
            }

            return new TestFileDescriptor(name, file, simulateBlock, fileSetup, fileTeardown,
                                          testCases, varsBlock, paramsBlock);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse test file: " + file, e);
        }
    }
}
