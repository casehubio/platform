package io.casehub.platform.simulation.restclient.generator;

import io.casehub.platform.simulation.restclient.generator.test.TestMixedClient;
import io.casehub.platform.simulation.restclient.generator.test.TestNoConfigKeyClient;
import io.casehub.platform.simulation.restclient.generator.test.TestRestClient;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientSimulationProcessorTest {

    private static IndexView index;

    @BeforeAll
    static void buildIndex() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(TestRestClient.class);
        indexer.indexClass(TestNoConfigKeyClient.class);
        indexer.indexClass(TestMixedClient.class);
        indexer.indexClass(io.casehub.platform.simulation.SimulationEligible.class);
        indexer.indexClass(org.eclipse.microprofile.rest.client.inject.RegisterRestClient.class);
        index = indexer.complete();
    }

    @Test
    void generatesDecoratorForRegisterRestClient() {
        var processor = new RestClientSimulationProcessor();
        List<RestClientSimulationProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        assertThat(sources).hasSize(2);
        assertThat(sources.stream().map(RestClientSimulationProcessor.GeneratedSource::className))
                .containsExactlyInAnyOrder(
                        "io.casehub.platform.simulation.generated.SimulatedTestRestClient",
                        "io.casehub.platform.simulation.generated.SimulatedTestNoConfigKeyClient");
    }

    @Test
    void skipsInterfaceWithSimulationEligible() {
        var processor = new RestClientSimulationProcessor();
        List<RestClientSimulationProcessor.GeneratedSource> sources = processor.generateFromIndex(index);

        assertThat(sources.stream().map(RestClientSimulationProcessor.GeneratedSource::className))
                .doesNotContain(
                        "io.casehub.platform.simulation.generated.SimulatedTestMixedClient");
    }

    @Test
    void generatedDecoratorHasRestClientQualifier() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("@Inject @Delegate @RestClient");
        assertThat(source).contains(
                "import org.eclipse.microprofile.rest.client.inject.RestClient;");
    }

    @Test
    void generatedDecoratorHasDecoratorAnnotations() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("@Decorator");
        assertThat(source).contains("@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 200)");
        assertThat(source).contains("implements TestRestClient");
    }

    @Test
    void generatedDecoratorImportsRestInvocation() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("import io.casehub.platform.simulation.RestInvocation;");
        assertThat(source).contains("import java.util.Map;");
    }

    @Test
    void methodUsesRestInvocationWithHttpMetadata() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("new RestInvocation(");
        assertThat(source).contains("\"test-api\"");
        assertThat(source).contains("\"getItem\"");
        assertThat(source).contains("\"GET\"");
        assertThat(source).contains("\"/api/items/{id}\"");
    }

    @Test
    void qualifiedNameUsesConfigKey() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("\"test-api.getItem\"");
        assertThat(source).contains("\"test-api.createItem\"");
        assertThat(source).contains("\"test-api.listItems\"");
    }

    @Test
    void postMethodDetected() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("\"POST\"");
    }

    @Test
    void pathParamsInParamsMap() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("\"id\", id");
    }

    @Test
    void queryParamsInParamsMap() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("\"page\", page");
        assertThat(source).contains("\"size\", size");
    }

    @Test
    void bodyParameterDetected() {
        var source = findSource("TestRestClient");

        assertThat(source).contains(", body)");
    }

    @Test
    void defaultMethodDelegatesWithoutSimulation() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("return delegate.healthCheck()");
    }

    @Test
    void defaultMethodHasNoSimulationLogic() {
        var source = findSource("TestRestClient");

        int healthIdx = source.indexOf("healthCheck");
        int braceEnd = source.indexOf("}\n", healthIdx);
        String healthSection = source.substring(healthIdx, braceEnd);
        assertThat(healthSection).doesNotContain("RestInvocation");
        assertThat(healthSection).doesNotContain("strategyFor");
    }

    @Test
    void noConfigKeyUsesKebabCasedClassName() {
        var source = findSource("TestNoConfigKeyClient");

        assertThat(source).contains("\"test-no-config-key-client.check\"");
    }

    @Test
    void simulationFlowPresent() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("simulation.strategyFor(qualifiedName)");
        assertThat(source).contains("strategy.get().canResolve(input)");
        assertThat(source).contains("strategy.get().resolve(input)");
        assertThat(source).contains("simulation.captureEnabled(qualifiedName)");
        assertThat(source).contains("simulation.capture(qualifiedName, currentPrincipal.tenancyId(), input,");
    }

    @Test
    void combinedPathFromClassAndMethod() {
        var source = findSource("TestRestClient");

        assertThat(source).contains("\"/api/items/{id}\"");
        assertThat(source).contains("\"/api/items\"");
        assertThat(source).contains("\"/api/items/{id}/details\"");
    }

    private static String findSource(final String spiName) {
        var processor = new RestClientSimulationProcessor();
        List<RestClientSimulationProcessor.GeneratedSource> sources = processor.generateFromIndex(index);
        return sources.stream()
                .filter(s -> s.className().contains(spiName))
                .findFirst()
                .map(RestClientSimulationProcessor.GeneratedSource::sourceCode)
                .orElseThrow(() -> new AssertionError("No source for " + spiName));
    }
}
