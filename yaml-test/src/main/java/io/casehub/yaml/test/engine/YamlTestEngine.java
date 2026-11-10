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
        new io.casehub.yaml.step.catalog.AptPluginSource(new com.fasterxml.jackson.databind.ObjectMapper())
                .populate(pluginRegistry);
        var serviceRegistry = new MapServiceRegistry();
        serviceRegistry.register(io.casehub.platform.api.expression.ExpressionEngineRegistry.class,
                                 new io.casehub.yaml.test.DotPathExpressionEngine());
        discoverServices(serviceRegistry);

        var simulationRuntime = new io.casehub.platform.simulation.SimulationRuntime(
                io.casehub.platform.simulation.MapSimulationConfig.builder().build(),
                new io.casehub.platform.simulation.inmem.InMemorySimulationCorpus<>());
        wrapWithSimulationDecorators(serviceRegistry, simulationRuntime);

        for (var child : root.getChildren()) {
            if (child instanceof YamlFileDescriptor fileDesc) {
                var effectiveRegistry = resolveFileServices(fileDesc.testFileDescriptor(), serviceRegistry);
                executeMultiTestFile(fileDesc, listener, pluginRegistry, effectiveRegistry, simulationRuntime);
            } else if (child instanceof YamlCaseDescriptor caseDesc && caseDesc.fileDescriptor() != null) {
                var effectiveRegistry = resolveFileServices(caseDesc.fileDescriptor(), serviceRegistry);
                executeSingleTestFile(caseDesc, listener, pluginRegistry, effectiveRegistry, simulationRuntime);
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
        return base;
    }

    private static MapServiceRegistry resolveFileServices(
            io.casehub.yaml.test.TestFileDescriptor fileDesc,
            MapServiceRegistry baseRegistry) {
        String fileProfile = fileDesc.servicesProfile();
        if (fileProfile == null || fileProfile.isEmpty()) {
            return baseRegistry;
        }
        var child = new MapServiceRegistry(baseRegistry);
        discoverServices(child, fileProfile);
        return child;
    }


    private static void discoverServices(MapServiceRegistry registry) {
        String profile = System.getProperty("casehub.yaml.services.profile");
        discoverServices(registry, profile);
    }

    @SuppressWarnings("unchecked")
    private static void discoverServices(MapServiceRegistry registry, String profile) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        var cl = Thread.currentThread().getContextClassLoader();
        String filename = (profile != null && !profile.isEmpty())
                          ? "META-INF/yaml-services-" + profile + ".yaml"
                          : "META-INF/yaml-services.yaml";
        java.util.Enumeration<java.net.URL> urls;
        try {
            urls = cl.getResources(filename);
        } catch (java.io.IOException e) {
            java.util.logging.Logger.getLogger(YamlTestEngine.class.getName())
                                    .log(java.util.logging.Level.WARNING, "Failed to discover " + filename, e);
            return;
        }
        while (urls.hasMoreElements()) {
            var url = urls.nextElement();
            try (var stream = url.openStream()) {
                var mappings = (java.util.Map<String, String>) mapper.readValue(stream, java.util.Map.class);
                if (mappings == null) {continue;}
                for (var entry : mappings.entrySet()) {
                    try {
                        Class<?> spiType = Class.forName(entry.getKey(), true, cl);
                        Object instance = Class.forName(entry.getValue(), true, cl)
                                               .getDeclaredConstructor().newInstance();
                        registry.register((Class) spiType, instance);
                    } catch (Exception e) {
                        java.util.logging.Logger.getLogger(YamlTestEngine.class.getName())
                                                .log(java.util.logging.Level.WARNING,
                                                     "Skipping service " + entry.getKey() + " → " + entry.getValue(), e);
                    }
                }
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(YamlTestEngine.class.getName())
                                        .log(java.util.logging.Level.WARNING, "Failed to process " + url, e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wrapWithSimulationDecorators(MapServiceRegistry registry,
                                                     io.casehub.platform.simulation.SimulationRuntime runtime) {
        try {
            var cl    = Thread.currentThread().getContextClassLoader();
            var urls  = cl.getResources("META-INF/simulation-decorators.properties");
            var props = new java.util.Properties();
            while (urls.hasMoreElements()) {
                try (var stream = urls.nextElement().openStream()) {
                    props.load(stream);
                }
            }
            for (var entry : props.entrySet()) {
                String spiClassName       = (String) entry.getKey();
                String decoratorClassName = (String) entry.getValue();
                try {
                    Class    spiType        = Class.forName(spiClassName, true, cl);
                    Object   service        = registry.lookup(spiType);
                    Class<?> decoratorClass = Class.forName(decoratorClassName, true, cl);
                    Object decorator = decoratorClass
                                               .getDeclaredConstructor(spiType, io.casehub.platform.simulation.SimulationRuntime.class)
                                               .newInstance(service, runtime);
                    registry.register(spiType, decorator);
                    if (service instanceof io.casehub.platform.simulation.Seedable seedable) {
                        String spiPrefix = toKebabCase(spiType.getSimpleName());
                        runtime.registerSeedable(spiPrefix, seedable);
                    }
                } catch (IllegalArgumentException ignored) {
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger(YamlTestEngine.class.getName())
                                            .log(java.util.logging.Level.FINE, "Skipping simulation decorator for " + spiClassName, e);
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(YamlTestEngine.class.getName())
                                    .log(java.util.logging.Level.WARNING, "Failed to load simulation-decorators.properties", e);
        }
    }

    private static String toKebabCase(String name) {
        var sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
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
