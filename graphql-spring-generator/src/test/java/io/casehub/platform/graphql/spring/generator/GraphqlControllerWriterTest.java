package io.casehub.platform.graphql.spring.generator;

import com.palantir.javapoet.JavaFile;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphqlControllerWriterTest {

    private static DomainDescriptor descriptor;

    @BeforeAll
    static void scan() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleDomainSpi.class);
        Index index = indexer.complete();

        var scanner = new McpDomainScanner();
        descriptor = scanner.scan(index).get(0);
    }

    @Test
    void generatesControllerAnnotation() {
        var writer = new GraphqlControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("@Controller");
        assertThat(source).contains("class SampleGraphqlController");
    }

    @Test
    void generatesQueryMapping() {
        var writer = new GraphqlControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("@QueryMapping");
    }

    @Test
    void generatesMutationMapping() {
        var writer = new GraphqlControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("@MutationMapping");
    }

    @Test
    void generatesArgumentAnnotations() {
        var writer = new GraphqlControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("@Argument");
    }

    @Test
    void injectsSpiInterface() {
        var writer = new GraphqlControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("SampleDomainSpi");
        assertThat(source).contains("this.sampleDomainSpi =");
    }

    @Test
    void generatesRestControllerForDomain() {
        var writer = new DomainRestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("@RestController");
        assertThat(source).contains("/api/sample");
        assertThat(source).contains("@GetMapping");
        assertThat(source).contains("@PostMapping");
        assertThat(source).contains("ResponseEntity");
    }
}
