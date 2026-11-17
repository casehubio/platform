package io.casehub.yaml.test.catalog;

import io.casehub.yaml.test.TestCase;
import io.casehub.yaml.test.TestFileDescriptor;
import io.casehub.yaml.test.YamlTestDiscovery;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionQualityLintTest {

    private static final Set<String> TRIVIAL_TARGETS = Set.of(
            "result.succeeded", "result.stepCount", "result.valid"
    );

    @Test
    void allTestsMustHaveBehavioralAssertions() {
        Path testRoot = Path.of("src/test/playbooks");
        List<TestFileDescriptor> files = YamlTestDiscovery.discover(testRoot);

        List<String> violations = new ArrayList<>();

        for (var file : files) {
            if (isLintSkipped(file)) continue;

            for (var testCase : file.testCases()) {
                if (hasOnlyTrivialAssertions(testCase)) {
                    violations.add(file.file().getFileName() + " > "
                            + testCase.name());
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Test cases with only trivial assertions "
                + "(succeeded/stepCount/valid):\n  - "
                + String.join("\n  - ", violations)
                + "\n\nAdd behavioral assertions or mark the file "
                + "with 'lint: skip'.");
    }

    @SuppressWarnings("unchecked")
    private boolean isLintSkipped(TestFileDescriptor file) {
        try (var is = java.nio.file.Files.newInputStream(file.file())) {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
            Map<String, Object> raw = mapper.readValue(is, Map.class);
            return "skip".equals(raw.get("lint"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasOnlyTrivialAssertions(TestCase testCase) {
        List<String> assertTargets = new ArrayList<>();
        for (var step : testCase.steps()) {
            Object target = step.get("assertThat");
            if (target != null) {
                assertTargets.add(String.valueOf(target));
            }
        }

        if (assertTargets.isEmpty()) {
            return false;
        }

        return assertTargets.stream().allMatch(TRIVIAL_TARGETS::contains);
    }
}
