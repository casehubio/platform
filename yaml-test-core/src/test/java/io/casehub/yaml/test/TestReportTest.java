package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestReportTest {

    @Test
    void junitXmlContainsTestSuiteAndTestCases() {
        var results = List.of(
                TestResult.pass("test1", Path.of("file1.test.yaml"), 100),
                TestResult.fail("test2", Path.of("file2.test.yaml"), 200,
                        List.of(new TestFailure(3, "assertThat", "Expected 'foo' but got 'bar'")))
        );

        var xml = TestReport.toJunitXml(results, "test-suite");
        assertThat(xml).contains("<testsuite");
        assertThat(xml).contains("name=\"test-suite\"");
        assertThat(xml).contains("tests=\"2\"");
        assertThat(xml).contains("failures=\"1\"");
        assertThat(xml).contains("<testcase name=\"test1\"");
        assertThat(xml).contains("<testcase name=\"test2\"");
        assertThat(xml).contains("<failure message=");
    }

    @Test
    void errorTestCaseIncludesErrorElement() {
        var results = List.of(
                TestResult.error("broken", Path.of("broken.test.yaml"), 50, "NullPointerException")
        );

        var xml = TestReport.toJunitXml(results, "errors");
        assertThat(xml).contains("errors=\"1\"");
        assertThat(xml).contains("<error message=");
    }

    @Test
    void skippedTestCaseIncludesSkippedElement() {
        var results = List.of(
                TestResult.skip("skipped", Path.of("skip.test.yaml"), "Requires CDI context")
        );

        var xml = TestReport.toJunitXml(results, "skip-suite");
        assertThat(xml).contains("<skipped");
    }

    @Test
    void emptyResultsProducesValidXml() {
        var xml = TestReport.toJunitXml(List.of(), "empty");
        assertThat(xml).contains("tests=\"0\"");
        assertThat(xml).contains("failures=\"0\"");
    }
}
