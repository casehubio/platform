package io.casehub.platform.callback.generator;

import io.casehub.platform.api.mcp.CallbackEligible;
import io.casehub.platform.callback.generator.test.TestDefaultNameSpi;
import io.casehub.platform.callback.generator.test.TestProvisioner;
import io.casehub.platform.callback.generator.test.TestSelector;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackDecoratorProcessorTest {

    private static IndexView index;

    @BeforeAll
    static void buildIndex() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(CallbackEligible.class);
        indexer.indexClass(TestProvisioner.class);
        indexer.indexClass(TestSelector.class);
        indexer.indexClass(TestDefaultNameSpi.class);
        index = indexer.complete();
    }

    @Test
    void toKebabCase_camelCase() {
        assertThat(CallbackDecoratorProcessor.toKebabCase("WorkerProvisioner"))
                .isEqualTo("worker-provisioner");
    }

    @Test
    void toKebabCase_singleWord() {
        assertThat(CallbackDecoratorProcessor.toKebabCase("Provisioner"))
                .isEqualTo("provisioner");
    }

    @Test
    void toKebabCase_consecutiveUppercase() {
        assertThat(CallbackDecoratorProcessor.toKebabCase("SLABreachPolicy"))
                .isEqualTo("sla-breach-policy");
    }

    @Test
    void toKebabCase_alreadyLower() {
        assertThat(CallbackDecoratorProcessor.toKebabCase("provisioner"))
                .isEqualTo("provisioner");
    }

    @Test
    void generateDecoratorSource_fanOutTrue_voidMethod() {
        CallbackDecoratorProcessor processor = new CallbackDecoratorProcessor();
        List<CallbackDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        var provisionerSource = sources.stream()
                .filter(s -> s.className().contains("TestProvisioner"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No generated source for TestProvisioner"));

        String code = provisionerSource.sourceCode();

        assertThat(code).contains("@Decorator");
        assertThat(code).contains("@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 100)");
        assertThat(code).contains("implements TestProvisioner");
        assertThat(code).contains("@Inject @Delegate TestProvisioner delegate");
        assertThat(code).contains("@Inject CallbackRegistry callbackRegistry");
        assertThat(code).contains("@Inject CallbackInvoker invoker");
        assertThat(code).contains("@Inject CurrentPrincipal currentPrincipal");
        assertThat(code).contains("callbackRegistry.findBySpi(\"test-provisioner\"");
        assertThat(code).contains("delegate.provision(");
        // fan-out: iterates all registrations
        assertThat(code).contains("for (CallbackRegistration reg : registrations)");
        assertThat(code).contains("invoker.invoke(reg, \"provision\"");
    }

    @Test
    void generateDecoratorSource_fanOutFalse_returnValue() {
        CallbackDecoratorProcessor processor = new CallbackDecoratorProcessor();
        List<CallbackDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        var selectorSource = sources.stream()
                .filter(s -> s.className().contains("TestSelector"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No generated source for TestSelector"));

        String code = selectorSource.sourceCode();

        assertThat(code).contains("@Decorator");
        assertThat(code).contains("implements TestSelector");
        assertThat(code).contains("callbackRegistry.findBySpi(\"test-selector\"");
        // single-impl: first registration wins, no loop
        assertThat(code).contains("registrations.get(0)");
        assertThat(code).doesNotContain("for (CallbackRegistration reg : registrations)");
        assertThat(code).contains("return invoker.invoke(reg, \"selectWorker\"");
        assertThat(code).contains("String.class");
    }

    @Test
    void generateDecoratorSource_defaultName_derivedFromClassName() {
        CallbackDecoratorProcessor processor = new CallbackDecoratorProcessor();
        List<CallbackDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        var defaultNameSource = sources.stream()
                .filter(s -> s.className().contains("TestDefaultNameSpi"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No generated source for TestDefaultNameSpi"));

        String code = defaultNameSource.sourceCode();

        assertThat(code).contains("callbackRegistry.findBySpi(\"test-default-name-spi\"");
    }

    @Test
    void generatesSourceForAllCallbackEligibleInterfaces() {
        CallbackDecoratorProcessor processor = new CallbackDecoratorProcessor();
        List<CallbackDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        List<String> classNames = sources.stream()
                .map(CallbackDecoratorProcessor.GeneratedSource::className)
                .toList();

        assertThat(classNames).hasSize(3);
        assertThat(classNames).anyMatch(n -> n.contains("TestProvisioner"));
        assertThat(classNames).anyMatch(n -> n.contains("TestSelector"));
        assertThat(classNames).anyMatch(n -> n.contains("TestDefaultNameSpi"));
    }

    @Test
    void generatedDecoratorImportsAllRequiredTypes() {
        CallbackDecoratorProcessor processor = new CallbackDecoratorProcessor();
        List<CallbackDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        var source = sources.stream()
                .filter(s -> s.className().contains("TestProvisioner"))
                .findFirst()
                .orElseThrow();

        String code = source.sourceCode();

        assertThat(code).contains("import jakarta.decorator.Decorator;");
        assertThat(code).contains("import jakarta.decorator.Delegate;");
        assertThat(code).contains("import jakarta.annotation.Priority;");
        assertThat(code).contains("import jakarta.inject.Inject;");
        assertThat(code).contains("import io.casehub.platform.api.callback.CallbackRegistration;");
        assertThat(code).contains("import io.casehub.platform.api.callback.CallbackRegistry;");
        assertThat(code).contains("import io.casehub.platform.callback.CallbackInvoker;");
        assertThat(code).contains("import io.casehub.platform.api.identity.CurrentPrincipal;");
        assertThat(code).contains("import java.util.List;");
    }

    @Test
    void fanOutTrue_returnValueMethod_iteratesWithFallback() {
        Indexer indexer = new Indexer();
        try {
            indexer.indexClass(CallbackEligible.class);
            indexer.indexClass(FanOutReturnSpi.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        IndexView localIndex = indexer.complete();

        CallbackDecoratorProcessor processor = new CallbackDecoratorProcessor();
        List<CallbackDecoratorProcessor.GeneratedSource> sources = processor.generateFromIndex(localIndex);

        var source = sources.stream()
                .filter(s -> s.className().contains("FanOutReturnSpi"))
                .findFirst()
                .orElseThrow();

        String code = source.sourceCode();

        // fan-out with return: iterate, return first non-null, else delegate fallback
        assertThat(code).contains("for (CallbackRegistration reg : registrations)");
        assertThat(code).contains("if (result != null) return result");
        assertThat(code).contains("return delegate.classify(");
    }

    @CallbackEligible(name = "fan-out-return", fanOut = true)
    interface FanOutReturnSpi {
        String classify(String input);
    }
}
