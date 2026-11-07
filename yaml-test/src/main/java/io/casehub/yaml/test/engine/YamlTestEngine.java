package io.casehub.yaml.test.engine;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.AssertThatPlugin;
import io.casehub.yaml.step.plugin.EmitEventPlugin;
import io.casehub.yaml.step.plugin.VerifySimulationPlugin;
import io.casehub.yaml.test.AgentSessionTestSupport;
import io.casehub.yaml.test.RunPlaybookPlugin;
import io.casehub.yaml.test.TestFileDescriptor;
import io.casehub.yaml.test.TestResult;
import io.casehub.yaml.test.TestStatus;
import io.casehub.yaml.test.ValidatePlaybookPlugin;
import io.casehub.yaml.test.YamlTestDiscovery;
import io.casehub.yaml.test.YamlTestExecutor;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.FileSource;

import java.nio.file.Path;
import java.util.List;

public class YamlTestEngine implements TestEngine {

    static final String ENGINE_ID = "casehub-yaml";

    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId engineId) {
        var engineDescriptor = new EngineDescriptor(engineId, "CaseHub YAML Tests");

        for (var selector : request.getSelectorsByType(ClasspathRootSelector.class)) {
            discoverFromRoot(selector.getClasspathRoot().getPath(), engineDescriptor);
        }
        for (var selector : request.getSelectorsByType(DirectorySelector.class)) {
            discoverFromRoot(selector.getPath().toString(), engineDescriptor);
        }

        return engineDescriptor;
    }

    private void discoverFromRoot(String rootPath, EngineDescriptor engine) {
        Path root = Path.of(rootPath);
        List<TestFileDescriptor> files = YamlTestDiscovery.discover(root);
        for (var file : files) {
            if (file.isMultiTest()) {
                var fileDescriptor = new YamlFileDescriptor(
                        engine.getUniqueId().append("file", file.file().toString()),
                        file.name(), file.file(), file);
                for (int i = 0; i < file.testCases().size(); i++) {
                    var testCase = file.testCases().get(i);
                    var caseDescriptor = new YamlCaseDescriptor(
                            fileDescriptor.getUniqueId().append("case", String.valueOf(i)),
                            testCase.name(), file.file());
                    fileDescriptor.addChild(caseDescriptor);
                }
                engine.addChild(fileDescriptor);
            } else {
                var testDescriptor = new YamlCaseDescriptor(
                        engine.getUniqueId().append("file", file.file().toString()),
                        file.name(), file.file());
                testDescriptor.setFileDescriptor(file);
                engine.addChild(testDescriptor);
            }
        }
    }

    @Override
    public void execute(ExecutionRequest request) {
        var root     = request.getRootTestDescriptor();
        var listener = request.getEngineExecutionListener();
        listener.executionStarted(root);

        var pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(AssertThatPlugin.definition());
        pluginRegistry.register(EmitEventPlugin.definition());
        pluginRegistry.register(RunPlaybookPlugin.definition());
        pluginRegistry.register(VerifySimulationPlugin.definition());
        pluginRegistry.register(ValidatePlaybookPlugin.definition());
        var serviceRegistry = new MapServiceRegistry();
        serviceRegistry.register(io.casehub.platform.api.expression.ExpressionEngineRegistry.class,
                                 new io.casehub.yaml.test.DotPathExpressionEngine());

        var simulationRuntime = new io.casehub.platform.simulation.SimulationRuntime(
                io.casehub.platform.simulation.MapSimulationConfig.builder().build(),
                new io.casehub.platform.simulation.inmem.InMemorySimulationCorpus<>());

        for (var child : root.getChildren()) {
            if (child instanceof YamlFileDescriptor fileDesc) {
                executeMultiTestFile(fileDesc, listener, pluginRegistry, serviceRegistry, simulationRuntime);
            } else if (child instanceof YamlCaseDescriptor caseDesc && caseDesc.fileDescriptor() != null) {
                executeSingleTestFile(caseDesc, listener, pluginRegistry, serviceRegistry, simulationRuntime);
            }
        }

        listener.executionFinished(root, TestExecutionResult.successful());
    }

    private void executeMultiTestFile(YamlFileDescriptor fileDesc,
                                      org.junit.platform.engine.EngineExecutionListener listener,
                                      CompositePluginRegistry pluginRegistry,
                                      MapServiceRegistry serviceRegistry,
                                      io.casehub.platform.simulation.SimulationRuntime simulationRuntime) {
        listener.executionStarted(fileDesc);
        Path resourceRoot = fileDesc.file().getParent();

        var                     fileRegistry = createFilePluginRegistry(pluginRegistry);
        AgentSessionTestSupport agentSupport = null;
        var                     testFileDesc = fileDesc.testFileDescriptor();
        if (testFileDesc.simulateBlock() != null
            && testFileDesc.simulateBlock().containsKey("agent")) {
            agentSupport = AgentSessionTestSupport.setup(
                    testFileDesc.simulateBlock(), fileRegistry, null, simulationRuntime);
        }

        try {
            var              executor = new YamlTestExecutor(fileRegistry, serviceRegistry, resourceRoot, simulationRuntime);
            List<TestResult> results  = executor.execute(testFileDesc);

            int childIdx = 0;
            for (var child : fileDesc.getChildren()) {
                if (child instanceof YamlCaseDescriptor caseDesc && childIdx < results.size()) {
                    listener.executionStarted(caseDesc);
                    var result = results.get(childIdx);
                    listener.executionFinished(caseDesc, toExecutionResult(result));
                    childIdx++;
                }
            }

            boolean allPassed = results.stream().allMatch(r -> r.status() == TestStatus.PASS);
            listener.executionFinished(fileDesc,
                                       allPassed ? TestExecutionResult.successful() : TestExecutionResult.failed(
                                               new AssertionError("One or more tests failed")));
        } finally {
            if (agentSupport != null) {agentSupport.close();}
        }
    }

    private void executeSingleTestFile(YamlCaseDescriptor caseDesc,
                                       org.junit.platform.engine.EngineExecutionListener listener,
                                       CompositePluginRegistry pluginRegistry,
                                       MapServiceRegistry serviceRegistry,
                                       io.casehub.platform.simulation.SimulationRuntime simulationRuntime) {
        listener.executionStarted(caseDesc);
        Path resourceRoot = caseDesc.file().getParent();

        var                     fileRegistry = createFilePluginRegistry(pluginRegistry);
        AgentSessionTestSupport agentSupport = null;
        var                     testFileDesc = caseDesc.fileDescriptor();
        if (testFileDesc.simulateBlock() != null
            && testFileDesc.simulateBlock().containsKey("agent")) {
            agentSupport = AgentSessionTestSupport.setup(
                    testFileDesc.simulateBlock(), fileRegistry, null, simulationRuntime);
        }

        try {
            var              executor = new YamlTestExecutor(fileRegistry, serviceRegistry, resourceRoot, simulationRuntime);
            List<TestResult> results  = executor.execute(testFileDesc);

            if (results.isEmpty()) {
                listener.executionFinished(caseDesc, TestExecutionResult.successful());
            } else {
                listener.executionFinished(caseDesc, toExecutionResult(results.get(0)));
            }
        } finally {
            if (agentSupport != null) {agentSupport.close();}
        }
    }

    private TestExecutionResult toExecutionResult(TestResult result) {
        return switch (result.status()) {
            case PASS -> TestExecutionResult.successful();
            case FAIL -> {
                var sb = new StringBuilder();
                for (var f : result.failures()) {
                    sb.append(f.message()).append("\n");
                }
                yield TestExecutionResult.failed(new AssertionError(sb.toString().trim()));
            }
            case ERROR -> {
                String msg = result.failures().isEmpty() ? "Unknown error" : result.failures().get(0).message();
                yield TestExecutionResult.failed(new RuntimeException(msg));
            }
            case SKIP -> TestExecutionResult.aborted(null);
        };
    }

    private static CompositePluginRegistry createFilePluginRegistry(CompositePluginRegistry base) {
        var fileRegistry = new CompositePluginRegistry();
        fileRegistry.register(AssertThatPlugin.definition());
        fileRegistry.register(EmitEventPlugin.definition());
        fileRegistry.register(RunPlaybookPlugin.definition());
        fileRegistry.register(VerifySimulationPlugin.definition());
        fileRegistry.register(ValidatePlaybookPlugin.definition());
        return fileRegistry;
    }


    static class YamlFileDescriptor extends AbstractTestDescriptor {
        private final Path file;
        private final TestFileDescriptor testFileDescriptor;

        YamlFileDescriptor(UniqueId uniqueId, String name, Path file, TestFileDescriptor tfd) {
            super(uniqueId, name, FileSource.from(file.toFile()));
            this.file = file;
            this.testFileDescriptor = tfd;
        }

        Path file() { return file; }
        TestFileDescriptor testFileDescriptor() { return testFileDescriptor; }

        @Override
        public Type getType() { return Type.CONTAINER; }
    }

    static class YamlCaseDescriptor extends AbstractTestDescriptor {
        private final Path file;
        private TestFileDescriptor fileDescriptor;

        YamlCaseDescriptor(UniqueId uniqueId, String name, Path file) {
            super(uniqueId, name, FileSource.from(file.toFile()));
            this.file = file;
        }

        Path file() { return file; }
        TestFileDescriptor fileDescriptor() { return fileDescriptor; }
        void setFileDescriptor(TestFileDescriptor fd) { this.fileDescriptor = fd; }

        @Override
        public Type getType() { return Type.TEST; }
    }

    static class AssertionError extends java.lang.AssertionError {
        AssertionError(String message) { super(message); }
    }
}
