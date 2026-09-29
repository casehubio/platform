package io.casehub.platform.rest.spring.generator;

import com.palantir.javapoet.JavaFile;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestControllerWriterTest {

    private static RestResourceDescriptor descriptor;
    private static RestResourceDescriptor sseDescriptor;
    private static RestResourceDescriptor statusDescriptor;
    private static RestResourceDescriptor multipartDescriptor;
    private static RestResourceDescriptor multivaluedHeaderDescriptor;


    @BeforeAll
    static void scan() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleResource.class);
        indexer.indexClass(SampleCore.class);
        Index index = indexer.complete();

        var                          scanner     = new RestResourceScanner();
        List<RestResourceDescriptor> descriptors = scanner.scan(index);
        descriptor = descriptors.get(0);

        var sseIndexer = new Indexer();
        sseIndexer.indexClass(SampleSseResource.class);
        sseIndexer.indexClass(SampleSseCore.class);
        Index                        sseIndex       = sseIndexer.complete();
        List<RestResourceDescriptor> sseDescriptors = scanner.scan(sseIndex);
        if (!sseDescriptors.isEmpty()) {
            sseDescriptor = sseDescriptors.get(0);
        }

        var statusIndexer = new Indexer();
        statusIndexer.indexClass(StatusBearingResource.class);
        statusIndexer.indexClass(StatusBearingCore.class);
        statusIndexer.indexClass(StatusBearingResult.class);
        Index                        statusIndex       = statusIndexer.complete();
        List<RestResourceDescriptor> statusDescriptors = scanner.scan(statusIndex);
        if (!statusDescriptors.isEmpty()) {
            statusDescriptor = statusDescriptors.get(0);
        }

        var multipartIndexer = new Indexer();
        multipartIndexer.indexClass(SampleMultipartResource.class);
        multipartIndexer.indexClass(SampleMultipartCore.class);
        Index multipartIndex = multipartIndexer.complete();
        List<RestResourceDescriptor> multipartDescriptors = scanner.scan(multipartIndex);
        if (!multipartDescriptors.isEmpty()) {
            multipartDescriptor = multipartDescriptors.get(0);
        }

        var mvhIndexer = new Indexer();
        mvhIndexer.indexClass(MultivaluedHeaderResource.class);
        mvhIndexer.indexClass(MultivaluedHeaderCore.class);
        Index                        mvhIndex       = mvhIndexer.complete();
        List<RestResourceDescriptor> mvhDescriptors = scanner.scan(mvhIndex);
        if (!mvhDescriptors.isEmpty()) {
            multivaluedHeaderDescriptor = mvhDescriptors.get(0);
        }
    }

    @Test
    void generatesRestControllerAnnotation() {
        var writer = new RestControllerWriter();
        JavaFile javaFile = writer.generate(descriptor, "io.casehub.platform.rest.spring.generator.spring");
        String source = javaFile.toString();

        assertThat(source).contains("@RestController");
        assertThat(source).contains("@RequestMapping(")
                .contains("\"/items\"");
    }

    @Test
    void generatesHttpMethodMappings() {
        var writer = new RestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("@GetMapping");
        assertThat(source).contains("@PostMapping");
        assertThat(source).contains("@DeleteMapping(\"/{id}\")");
    }

    @Test
    void generatesParameterAnnotations() {
        var writer = new RestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("@PathVariable(\"id\")");
        assertThat(source).contains("@RequestParam(\"tenancyId\")");
        assertThat(source).contains("@RequestBody");
    }

    @Test
    void generatesConstructorInjection() {
        var writer = new RestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("SampleCore");
        assertThat(source).contains("this.core =");
    }

    @Test
    void mapsVoidReturnToNoContent() {
        var writer = new RestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("ResponseEntity.noContent().build()");
    }

    @Test
    void mapsOptionalReturnToOkOrNotFound() {
        var writer = new RestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains(".map(ResponseEntity::ok)");
        assertThat(source).contains("ResponseEntity.notFound().build()");
    }

    @Test
    void mapsNonVoidNonOptionalReturnToOk() {
        var writer = new RestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("ResponseEntity.ok(");
    }

    @Test
    void generatesMediaTypeAttributes() {
        var writer = new RestControllerWriter();
        String source = writer.generate(descriptor, "test.spring").toString();

        assertThat(source).contains("MediaType.APPLICATION_JSON_VALUE");
    }

    @Test
    void mapsStatusBearingReturnToResponseEntityWithStatus() {
        assertThat(statusDescriptor).isNotNull();
        var writer = new RestControllerWriter();
        String source = writer.generate(statusDescriptor, "test.spring").toString();

        assertThat(source).contains("ResponseEntity.status(result.status()).body(result)");
        assertThat(source).doesNotContain("ResponseEntity.noContent()");
    }

    @Test
    void generatesMultipartFileParameter() {
        assertThat(multipartDescriptor).isNotNull();
        var writer = new RestControllerWriter();
        String source = writer.generate(multipartDescriptor, "test.spring").toString();

        assertThat(source).contains("@RequestPart(\"file\") MultipartFile file");
        assertThat(source).contains("file.getBytes()");
        assertThat(source).contains("file.getOriginalFilename()");
        assertThat(source).contains("IOException");
        assertThat(source).doesNotContain("FileUpload");
    }

    @Test
    void generatesMultivaluedHeadersHelper() {
        assertThat(multivaluedHeaderDescriptor).isNotNull();
        var writer = new RestControllerWriter();
        String source = writer.generate(multivaluedHeaderDescriptor, "test.spring").toString();

        assertThat(source).contains("extractMultivaluedHeaders(httpRequest)");
        assertThat(source).contains("Map<String, List<String>> headers");
        assertThat(source).contains("request.getHeaders(name)");
    }

    @Test
    void generatesFlatHeadersHelper() {
        assertThat(multivaluedHeaderDescriptor).isNotNull();
        var writer = new RestControllerWriter();
        String source = writer.generate(multivaluedHeaderDescriptor, "test.spring").toString();

        assertThat(source).contains("extractHeaders(httpRequest)");
        assertThat(source).contains("Map<String, String> headers");
        assertThat(source).contains("request.getHeader(name)");
    }

    @Test
    void generatesSseEmitterForFlowPublisher() {
        assertThat(sseDescriptor).isNotNull();
        var    writer = new RestControllerWriter();
        String source = writer.generate(sseDescriptor, "test.spring").toString();
        assertThat(source).contains("SseEmitter");
        assertThat(source).contains("subscribe");
        assertThat(source).doesNotContain("Flow.Publisher");
        assertThat(source).contains("Flow.Subscription subscription");
        assertThat(source).doesNotContain("Flow.Subscription<");
    }
}
