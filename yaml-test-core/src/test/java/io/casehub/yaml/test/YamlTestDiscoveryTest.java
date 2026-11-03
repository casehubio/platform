package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YamlTestDiscoveryTest {

    private static final Path SUITE_ROOT = Path.of("src/test/resources/test-suite");

    @Test
    void discoversTestYamlFilesInSortedOrder() {
        var descriptors = YamlTestDiscovery.discover(SUITE_ROOT);
        assertThat(descriptors).hasSize(2);
        assertThat(descriptors).extracting(TestFileDescriptor::name)
                .containsExactly("Basic assertion test", "Multi-test file");
    }

    @Test
    void singleTestFileProducesOneTestCase() {
        var descriptors = YamlTestDiscovery.discover(SUITE_ROOT);
        var basic = descriptors.get(0);
        assertThat(basic.testCases()).hasSize(1);
        assertThat(basic.testCases().get(0).name()).isEqualTo("Basic assertion test");
        assertThat(basic.testCases().get(0).steps()).isNotEmpty();
        assertThat(basic.simulateBlock()).isNull();
        assertThat(basic.isMultiTest()).isFalse();
    }

    @Test
    void multiTestFileProducesMultipleTestCases() {
        var descriptors = YamlTestDiscovery.discover(SUITE_ROOT);
        var multi = descriptors.get(1);
        assertThat(multi.testCases()).hasSize(2);
        assertThat(multi.testCases()).extracting(TestCase::name)
                .containsExactly("First test", "Second test");
        assertThat(multi.simulateBlock()).isNull();
        assertThat(multi.fileSetup()).isNotEmpty();
        assertThat(multi.fileTeardown()).isNotEmpty();
        assertThat(multi.isMultiTest()).isTrue();
    }

    @Test
    void multiTestPerTestLifecyclePreserved() {
        var descriptors = YamlTestDiscovery.discover(SUITE_ROOT);
        var multi = descriptors.get(1);
        var second = multi.testCases().get(1);
        assertThat(second.setupSteps()).isNotEmpty();
        assertThat(second.teardownSteps()).isNotEmpty();
    }

    @Test
    void excludesLifecycleFiles() throws Exception {
        var lifecycleDir = Path.of("src/test/resources/test-suite-lifecycle");
        var dir = lifecycleDir.toFile();
        dir.mkdirs();
        var setup = new java.io.File(dir, "_setup.test.yaml");
        var teardown = new java.io.File(dir, "_teardown.test.yaml");
        var test = new java.io.File(dir, "01-actual.test.yaml");

        setup.createNewFile();
        teardown.createNewFile();
        java.nio.file.Files.writeString(test.toPath(),
                "name: Actual test\nsteps:\n  - assert:\n      expression: 'true'\n");

        try {
            var descriptors = YamlTestDiscovery.discover(lifecycleDir);
            assertThat(descriptors).hasSize(1);
            assertThat(descriptors.get(0).name()).isEqualTo("Actual test");
        } finally {
            setup.delete();
            teardown.delete();
            test.delete();
            dir.delete();
        }
    }

    @Test
    void emptyDirectoryReturnsEmptyList() {
        var empty = Path.of("src/test/resources/empty-test-suite");
        empty.toFile().mkdirs();
        try {
            var descriptors = YamlTestDiscovery.discover(empty);
            assertThat(descriptors).isEmpty();
        } finally {
            empty.toFile().delete();
        }
    }
}
