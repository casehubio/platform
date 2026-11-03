package io.casehub.yaml.test;

import java.nio.file.Path;
import java.util.List;

public record TestResult(
        TestStatus status,
        String name,
        Path file,
        List<TestFailure> failures,
        long durationMs) {

    public static TestResult pass(String name, Path file, long durationMs) {
        return new TestResult(TestStatus.PASS, name, file, List.of(), durationMs);
    }

    public static TestResult fail(String name, Path file, long durationMs, List<TestFailure> failures) {
        return new TestResult(TestStatus.FAIL, name, file, List.copyOf(failures), durationMs);
    }

    public static TestResult error(String name, Path file, long durationMs, String message) {
        return new TestResult(TestStatus.ERROR, name, file,
                List.of(new TestFailure(-1, null, message)), durationMs);
    }

    public static TestResult skip(String name, Path file, String reason) {
        return new TestResult(TestStatus.SKIP, name, file,
                List.of(new TestFailure(-1, null, reason)), 0);
    }
}
