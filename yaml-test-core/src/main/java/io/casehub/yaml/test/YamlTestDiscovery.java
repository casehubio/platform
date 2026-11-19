package io.casehub.yaml.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

import java.io.IOException;
import java.io.InputStream;
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
            List<Object> documents = readAllDocuments(file);
            if (!(documents.get(0) instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(file + ": test file must start with a metadata document (name:), not a bare list.");
            }
            Map<String, Object> doc = (Map<String, Object>) documents.get(0);

            if (doc.containsKey("steps") && doc.get("steps") instanceof List<?>) {
                throw new IllegalArgumentException(file + ": top-level 'steps:' is not allowed. "
                        + "Use bare-list format: add '---' before the step list and remove the 'steps:' line.");
            }

            String                    name            = (String) doc.getOrDefault("name", file.getFileName().toString());
            Map<String, Object>       simulateBlock   = (Map<String, Object>) doc.get("simulate");
            List<Map<String, Object>> fileSetup       = (List<Map<String, Object>>) doc.get("setup");
            List<Map<String, Object>> fileTeardown    = (List<Map<String, Object>>) doc.get("teardown");
            Map<String, Object>       varsBlock       = (Map<String, Object>) doc.get("vars");
            Map<String, Object>       paramsBlock     = (Map<String, Object>) doc.get("params");
            String                    servicesProfile = (String) doc.get("services");

            if (doc.containsKey("tests")) {
                throw new IllegalArgumentException(file + ": 'tests:' with nested 'steps:' is not allowed. "
                        + "Use multi-document format: each test is a metadata document (name:) followed by a bare step list after '---'.");
            }

            List<TestCase> testCases = parseTestCasesFromDocuments(name, documents);

            return new TestFileDescriptor(name, file, simulateBlock, fileSetup, fileTeardown,
                                          testCases, varsBlock, paramsBlock, servicesProfile);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse test file: " + file, e);
        }
    }

    private static List<Object> readAllDocuments(Path file) throws IOException {
        List<Object> documents = new ArrayList<>();
        try (InputStream is = Files.newInputStream(file);
             YAMLParser parser = (YAMLParser) new YAMLFactory().createParser(is)) {
            var iterator = MAPPER.readValues(parser, Object.class);
            while (iterator.hasNext()) {
                documents.add(iterator.next());
            }
        }
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("Empty test file: " + file);
        }
        return documents;
    }

    @SuppressWarnings("unchecked")
    private static List<TestCase> parseTestCasesFromDocuments(String fileName, List<Object> documents) {
        if (documents.size() < 2) {
            return List.of(new TestCase(fileName, null, List.of(), null));
        }

        if (documents.size() == 2 && documents.get(1) instanceof List<?> stepList) {
            return List.of(new TestCase(fileName, null, (List<Map<String, Object>>) (List<?>) stepList, null));
        }

        List<TestCase> testCases = new ArrayList<>();
        int            i         = 1;
        while (i < documents.size()) {
            if (documents.get(i) instanceof Map<?, ?> metaDoc) {
                var    meta            = (Map<String, Object>) metaDoc;
                String testName        = (String) meta.getOrDefault("name", "test-" + (testCases.size() + 1));
                var    perTestSetup    = (List<Map<String, Object>>) meta.get("setup");
                var    perTestTeardown = (List<Map<String, Object>>) meta.get("teardown");
                var    perTestSimulate = (Map<String, Object>) meta.get("simulate");

                List<Map<String, Object>> steps = List.of();
                if (i + 1 < documents.size() && documents.get(i + 1) instanceof List<?> stepList) {
                    steps = (List<Map<String, Object>>) (List<?>) stepList;
                    i += 2;
                } else {
                    i++;
                }
                testCases.add(new TestCase(testName, perTestSetup, steps, perTestTeardown, perTestSimulate));
            } else if (documents.get(i) instanceof List<?> stepList) {
                testCases.add(new TestCase("test-" + (testCases.size() + 1), null,
                                           (List<Map<String, Object>>) (List<?>) stepList, null));
                i++;
            } else {
                i++;
            }
        }
        return testCases;
    }

}
