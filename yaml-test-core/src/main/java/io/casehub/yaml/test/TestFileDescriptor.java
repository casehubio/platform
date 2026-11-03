package io.casehub.yaml.test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record TestFileDescriptor(
        String name,
        Path file,
        Map<String, Object> simulateBlock,
        List<Map<String, Object>> fileSetup,
        List<Map<String, Object>> fileTeardown,
        List<TestCase> testCases,
        Map<String, Object> varsBlock,
        Map<String, Object> paramsBlock) {

    public TestFileDescriptor(String name, Path file, Map<String, Object> simulateBlock,
                              List<Map<String, Object>> fileSetup, List<Map<String, Object>> fileTeardown,
                              List<TestCase> testCases) {
        this(name, file, simulateBlock, fileSetup, fileTeardown, testCases, null, null);
    }

    public TestFileDescriptor {
        fileSetup    = fileSetup != null ? List.copyOf(fileSetup) : List.of();
        fileTeardown = fileTeardown != null ? List.copyOf(fileTeardown) : List.of();
        testCases    = List.copyOf(testCases);
    }

    public boolean isMultiTest() {
        return testCases.size() > 1 || (testCases.size() == 1 && !testCases.get(0).name().equals(name));
    }
}
