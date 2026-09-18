package io.casehub.platform.simulation.generator;

import io.casehub.platform.simulation.SimulationEligible;
import io.casehub.platform.simulation.generator.test.TestDefaultNameSpi;
import io.casehub.platform.simulation.generator.test.TestSimpleService;
import io.casehub.platform.simulation.generator.test.TestSpiWithDefaults;
import io.casehub.platform.simulation.generator.test.TestUnannotatedSpi;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationDecoratorProcessorTest {

    private static IndexView index;

    @BeforeAll
    static void buildIndex() throws IOException {
        final Indexer indexer = new Indexer();
        indexer.indexClass(SimulationEligible.class);
        indexer.indexClass(TestSimpleService.class);
        indexer.indexClass(TestDefaultNameSpi.class);
        indexer.indexClass(TestSpiWithDefaults.class);
        indexer.indexClass(TestUnannotatedSpi.class);
        index = indexer.complete();
    }

    // --- toKebabCase ---

    @Test
    void toKebabCase_camelCase() {
        assertThat(SimulationDecoratorProcessor.toKebabCase("TestSimpleService"))
                .isEqualTo("test-simple-service");
    }

    @Test
    void toKebabCase_singleWord() {
        assertThat(SimulationDecoratorProcessor.toKebabCase("Service"))
                .isEqualTo("service");
    }

    @Test
    void toKebabCase_consecutiveUppercase() {
        assertThat(SimulationDecoratorProcessor.toKebabCase("SLABreachPolicy"))
                .isEqualTo("sla-breach-policy");
    }

    // --- generates for all @SimulationEligible interfaces ---

    @Test
    void generatesSourceForAllSimulationEligibleInterfaces() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final List<String> classNames = sources.stream()
                .map(SimulationDecoratorProcessor.GeneratedSource::className)
                .toList();

        assertThat(classNames).hasSize(8);
        assertThat(classNames).anyMatch(n -> n.contains("SimulatedTestSimpleService"));
        assertThat(classNames).anyMatch(n -> n.contains("SimulatedTestDefaultNameSpi"));
    }

    // --- decorator structure ---

    @Test
    void generatedDecoratorHasCorrectAnnotationsAndFields() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("@Decorator");
        assertThat(code).contains("@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 200)");
        assertThat(code).contains("implements TestSimpleService");
        assertThat(code).contains("@Inject @Delegate TestSimpleService delegate");
        assertThat(code).contains("@Inject SimulationRuntime simulation");
        assertThat(code).contains("@Inject CurrentPrincipal currentPrincipal");
    }

    @Test
    void generatedDecoratorImportsAllRequiredTypes() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("import jakarta.decorator.Decorator;");
        assertThat(code).contains("import jakarta.decorator.Delegate;");
        assertThat(code).contains("import jakarta.annotation.Priority;");
        assertThat(code).contains("import jakarta.inject.Inject;");
        assertThat(code).contains("import io.casehub.platform.simulation.SimulationRuntime;");
        assertThat(code).contains("import io.casehub.platform.simulation.SimulationStrategy;");
        assertThat(code).contains("import io.casehub.platform.api.identity.CurrentPrincipal;");
    }

    // --- non-void method: simulation + capture ---

    @Test
    void nonVoidMethodChecksStrategyThenDelegatesWithCapture() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("\"test-service.lookup\"");
        assertThat(code).contains("simulation.strategyFor(qualifiedName)");
        assertThat(code).contains("strategy.get().canResolve(");
        assertThat(code).contains("strategy.get().resolve(");
        assertThat(code).contains("delegate.lookup(");
        assertThat(code).contains("simulation.captureEnabled(qualifiedName)");
        assertThat(code).contains("simulation.capture(qualifiedName");
    }

    // --- void method: simulation + capture ---

    @Test
    void voidMethodChecksStrategyThenDelegatesWithCapture() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("\"test-service.save\"");
        assertThat(code).contains("delegate.save(");
    }

    // --- primitive return type ---

    @Test
    void primitiveReturnTypeMethodWorks() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("\"test-service.count\"");
        assertThat(code).contains("delegate.count(");
    }

    // --- default name derived from class name ---

    @Test
    void defaultNameDerivedFromClassName() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestDefaultNameSpi");

        assertThat(code).contains("\"test-default-name-spi.process\"");
    }

    // --- default method handling ---

    @Test
    void allMethodsGetSimulationLogic() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);
        final String code = findSource(sources, "TestSpiWithDefaults");

        assertThat(code).contains("\"spi-with-defaults.query\"");
        assertThat(code).contains("\"spi-with-defaults.store\"");
        assertThat(code).contains("\"spi-with-defaults.queryAll\"");
        assertThat(code).contains("\"spi-with-defaults.count\"");
        assertThat(occurrences(code, "simulation.strategyFor")).isGreaterThanOrEqualTo(4);
    }

    // --- journal recording ---

    @Test
    void generatedSourceContainsJournalRecording() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);
        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("simulation.recordJournal(qualifiedName,");
        assertThat(occurrences(code, "simulation.recordJournal")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void journalRecordingForSimulatedPathUsesTrue() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);
        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("simResult, true)");
    }

    @Test
    void journalRecordingForDelegatePathUsesFalse() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);
        final String code = findSource(sources, "TestSimpleService");

        assertThat(code).contains("result, false)");
    }

    // --- listing file support ---

    @Test
    void listingFileRegistersUnannotatedSpi() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final List<String> classNames = sources.stream()
                .map(SimulationDecoratorProcessor.GeneratedSource::className)
                .toList();

        assertThat(classNames).anyMatch(n -> n.contains("SimulatedTestUnannotatedSpi"));
    }

    @Test
    void listingFileGeneratedDecoratorHasCorrectQualifiedNames() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);
        final String code = findSource(sources, "TestUnannotatedSpi");

        assertThat(code).contains("\"test-unannotated.resolve\"");
        assertThat(code).contains("\"test-unannotated.delete\"");
        assertThat(code).contains("implements TestUnannotatedSpi");
    }

    @Test
    void annotationTakesPrecedenceOverListingFile() {
        final var processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        final long testSimpleServiceCount = sources.stream()
                .filter(s -> s.className().contains("TestSimpleService"))
                .count();
        assertThat(testSimpleServiceCount).isEqualTo(2);
    }

    // --- helpers ---


// --- QN constants generation ---

    @Test
    void generatesQNConstantsClassForAnnotatedInterface() {
        final var                                                processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources   = processor.generateFromIndex(index);

        final List<String> classNames = sources.stream()
                                               .map(SimulationDecoratorProcessor.GeneratedSource::className)
                                               .toList();

        assertThat(classNames).anyMatch(n -> n.contains("TestSimpleServiceQN"));
    }

    @Test
    void qnConstantsClassContainsMethodConstants() {
        final var                                                processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources   = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestSimpleServiceQN");

        assertThat(code).contains("public static final String LOOKUP = \"test-service.lookup\"");
        assertThat(code).contains("public static final String SAVE = \"test-service.save\"");
        assertThat(code).contains("public static final String COUNT = \"test-service.count\"");
        assertThat(code).contains("private TestSimpleServiceQN()");
    }

    @Test
    void qnConstantsClassGeneratedForListingFileEntries() {
        final var                                                processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources   = processor.generateFromIndex(index);

        final List<String> classNames = sources.stream()
                                               .map(SimulationDecoratorProcessor.GeneratedSource::className)
                                               .toList();

        assertThat(classNames).anyMatch(n -> n.contains("TestUnannotatedSpiQN"));
    }

    @Test
    void qnListingFileClassHasCorrectConstants() {
        final var                                                processor = new SimulationDecoratorProcessor();
        final List<SimulationDecoratorProcessor.GeneratedSource> sources   = processor.generateFromIndex(index);

        final String code = findSource(sources, "TestUnannotatedSpiQN");

        assertThat(code).contains("public static final String RESOLVE = \"test-unannotated.resolve\"");
        assertThat(code).contains("public static final String DELETE = \"test-unannotated.delete\"");
    }

    private static int occurrences(final String text, final String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private static String findSource(final List<SimulationDecoratorProcessor.GeneratedSource> sources,
                                     final String spiName) {
        return sources.stream()
                .filter(s -> s.className().contains(spiName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No generated source for " + spiName))
                .sourceCode();
    }
}
