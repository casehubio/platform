package io.casehub.platform.confirmation.generator;

import io.casehub.platform.api.confirmation.RequiresConfirmation;
import io.casehub.platform.confirmation.generator.test.TestMixedSpi;
import io.casehub.platform.confirmation.generator.test.TestSensitiveSpi;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationDecoratorProcessorTest {

    private static IndexView index;

    @BeforeAll
    static void buildIndex() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(RequiresConfirmation.class);
        indexer.indexClass(TestSensitiveSpi.class);
        indexer.indexClass(TestMixedSpi.class);
        index = indexer.complete();
    }

    @Test
    void generates_decoratorForAnnotatedInterface() {
        var processor = new ConfirmationDecoratorProcessor();
        List<ConfirmationDecoratorProcessor.GeneratedSource> sources =
            processor.generateFromIndex(index);

        var sensitiveSource = sources.stream()
            .filter(s -> s.className().contains("TestSensitiveSpi"))
            .findFirst()
            .orElseThrow();

        String code = sensitiveSource.sourceCode();

        assertThat(code).contains("@Decorator");
        assertThat(code).contains("@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 50)");
        assertThat(code).contains("implements TestSensitiveSpi");
        assertThat(code).contains("@Inject @Delegate @Any TestSensitiveSpi delegate");
        assertThat(code).contains("@Inject ConfirmationInterceptorCore confirmationCore");
        assertThat(code).contains("@Inject CurrentPrincipal currentPrincipal");
        assertThat(code).contains("OperationDescriptor");
        assertThat(code).contains("confirmationCore.requireConfirmation");
        assertThat(code).contains("delegate.process(amount, currency)");
    }

    @Test
    void mixedSpi_annotatedMethodGetsConfirmation_unannotatedPassesThrough() {
        var processor = new ConfirmationDecoratorProcessor();
        List<ConfirmationDecoratorProcessor.GeneratedSource> sources =
            processor.generateFromIndex(index);

        var mixedSource = sources.stream()
            .filter(s -> s.className().contains("TestMixedSpi"))
            .findFirst()
            .orElseThrow();

        String code = mixedSource.sourceCode();

        assertThat(code).contains("confirmationCore.requireConfirmation");
        assertThat(code).contains("delegate.deleteResource(resourceId)");
        assertThat(code).contains("return delegate.getStatus(resourceId)");
    }

    @Test
    void generates_summaryTemplateExpression() {
        var processor = new ConfirmationDecoratorProcessor();
        List<ConfirmationDecoratorProcessor.GeneratedSource> sources =
            processor.generateFromIndex(index);

        var sensitiveSource = sources.stream()
            .filter(s -> s.className().contains("TestSensitiveSpi"))
            .findFirst()
            .orElseThrow();

        String code = sensitiveSource.sourceCode();

        assertThat(code).contains("String.valueOf(amount)");
        assertThat(code).contains("String.valueOf(currency)");
        assertThat(code).contains("Map.of(");
    }

    @Test
    void generatesOneDecoratorPerInterface() {
        var processor = new ConfirmationDecoratorProcessor();
        List<ConfirmationDecoratorProcessor.GeneratedSource> sources =
            processor.generateFromIndex(index);

        assertThat(sources).hasSize(2);
        assertThat(sources.stream().map(ConfirmationDecoratorProcessor.GeneratedSource::className))
            .containsExactlyInAnyOrder(
                "io.casehub.platform.confirmation.generated.ConfirmedTestSensitiveSpi",
                "io.casehub.platform.confirmation.generated.ConfirmedTestMixedSpi");
    }
}
