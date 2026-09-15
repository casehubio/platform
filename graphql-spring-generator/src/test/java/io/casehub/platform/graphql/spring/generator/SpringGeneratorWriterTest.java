package io.casehub.platform.graphql.spring.generator;

import com.palantir.javapoet.JavaFile;
import io.casehub.platform.generator.DomainScanResult;
import io.casehub.platform.generator.McpDomainJandexScanner;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringGeneratorWriterTest {

    private static DomainScanResult domain;

    @BeforeAll
    static void scan() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleDomainSpi.class);
        Index index = indexer.complete();

        var scanner = new McpDomainJandexScanner();
        domain = scanner.scan(index).get(0);
    }

    @Test
    void generatesGraphqlController() {
        var writer = new SpringGraphqlControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@Controller");
        assertThat(source).contains("class SampleGraphqlController");
    }

    @Test
    void generatesQueryMapping() {
        var writer = new SpringGraphqlControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@QueryMapping");
    }

    @Test
    void generatesMutationMapping() {
        var writer = new SpringGraphqlControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@MutationMapping");
    }

    @Test
    void generatesSubscriptionMappingForStream() {
        var writer = new SpringGraphqlControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@SubscriptionMapping");
        assertThat(source).contains("watchItems");
    }

    @Test
    void generatesRestController() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@RestController");
        assertThat(source).contains("/api/sample");
    }

    @Test
    void generatesRolesAllowed() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@RolesAllowed");
        assertThat(source).contains("\"admin\"");
    }

    @Test
    void generatesPathVariable() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@PathVariable");
        assertThat(source).contains("\"id\"");
        assertThat(source).contains("/{id}");
    }

    @Test
    void generatesRestNameAsRequestParam() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("@RequestParam(\"page_size\")");
    }

    @Test
    void generatesRestStatusOverride() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("status(201)");
    }

    @Test
    void generatesPaginatedResponseWithHeader() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("X-Total-Count");
        assertThat(source).contains("totalCount()");
    }

    @Test
    void generatesSseEmitterForStream() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("SseEmitter");
        assertThat(source).contains("TEXT_EVENT_STREAM_VALUE");
    }

    @Test
    void generatesKebabCasePaths() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("/list-items");
        assertThat(source).contains("/get-item");
        assertThat(source).contains("/create-item");
        assertThat(source).contains("/watch-items");
    }

    @Test
    void generatesNullCheckForPathParam() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("if (result == null)");
        assertThat(source).contains("notFound()");
    }

    @Test
    void mutationReturns201() {
        var writer = new SpringDomainRestControllerWriter();
        String source = writer.generate(domain, "test.spring").toString();

        assertThat(source).contains("status(201)");
    }
}
