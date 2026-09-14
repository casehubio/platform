package io.casehub.platform.rest.spring.generator;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderScannerTest {

    private static Index index;

    @BeforeAll
    static void buildIndex() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleExceptionMapper.class);
        indexer.indexClass(SampleRequestFilter.class);
        indexer.indexClass(SampleResource.class);
        indexer.indexClass(SampleCore.class);
        index = indexer.complete();
    }

    @Test
    void scansExceptionMapper() {
        var scanner = new ProviderScanner();
        List<ProviderDescriptor> descriptors = scanner.scan(index);

        ProviderDescriptor mapper = descriptors.stream()
                .filter(d -> d.providerKind() == ProviderDescriptor.ProviderKind.EXCEPTION_MAPPER)
                .findFirst().orElseThrow();

        assertThat(mapper.className()).isEqualTo(
                "io.casehub.platform.rest.spring.generator.SampleExceptionMapper");
        assertThat(mapper.targetType()).isEqualTo("java.lang.IllegalArgumentException");
    }

    @Test
    void scansRequestFilter() {
        var scanner = new ProviderScanner();
        List<ProviderDescriptor> descriptors = scanner.scan(index);

        ProviderDescriptor filter = descriptors.stream()
                .filter(d -> d.providerKind() == ProviderDescriptor.ProviderKind.REQUEST_FILTER)
                .findFirst().orElseThrow();

        assertThat(filter.className()).isEqualTo(
                "io.casehub.platform.rest.spring.generator.SampleRequestFilter");
        assertThat(filter.priority()).isEqualTo(900);
    }

    @Test
    void classifiesSecurityFilter() {
        var scanner = new ProviderScanner();
        List<ProviderDescriptor> descriptors = scanner.scan(index);

        ProviderDescriptor filter = descriptors.stream()
                .filter(d -> d.providerKind() == ProviderDescriptor.ProviderKind.REQUEST_FILTER)
                .findFirst().orElseThrow();

        assertThat(filter.isSecurityFilter()).isTrue();
    }

    @Test
    void doesNotScanPathResources() {
        var scanner = new ProviderScanner();
        List<ProviderDescriptor> descriptors = scanner.scan(index);

        assertThat(descriptors).noneMatch(d ->
                d.className().contains("SampleResource"));
    }
}
