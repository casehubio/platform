package io.casehub.platform.rest.spring.generator;

import com.palantir.javapoet.JavaFile;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderWriterTest {

    private static List<ProviderDescriptor> descriptors;

    @BeforeAll
    static void scan() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleExceptionMapper.class);
        indexer.indexClass(SampleRequestFilter.class);
        Index index = indexer.complete();

        var scanner = new ProviderScanner();
        descriptors = scanner.scan(index);
    }

    @Test
    void generatesControllerAdviceForExceptionMapper() {
        ProviderDescriptor mapper = descriptors.stream()
                .filter(d -> d.providerKind() == ProviderDescriptor.ProviderKind.EXCEPTION_MAPPER)
                .findFirst().orElseThrow();

        var writer = new ProviderWriter();
        JavaFile javaFile = writer.generate(mapper, "test.spring");
        String source = javaFile.toString();

        assertThat(source).contains("@ControllerAdvice");
        assertThat(source).contains("@ExceptionHandler(IllegalArgumentException.class)");
        assertThat(source).contains("ResponseEntity");
    }

    @Test
    void generatesFilterForSecurityRequestFilter() {
        ProviderDescriptor filter = descriptors.stream()
                .filter(d -> d.providerKind() == ProviderDescriptor.ProviderKind.REQUEST_FILTER)
                .findFirst().orElseThrow();

        var writer = new ProviderWriter();
        JavaFile javaFile = writer.generate(filter, "test.spring");
        String source = javaFile.toString();

        assertThat(source).contains("Filter");
        assertThat(source).contains("@Order(900)");
        assertThat(source).contains("@Component");
    }
}
