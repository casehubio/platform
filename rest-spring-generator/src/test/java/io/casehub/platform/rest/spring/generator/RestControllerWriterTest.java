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

    @BeforeAll
    static void scan() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleResource.class);
        indexer.indexClass(SampleCore.class);
        Index index = indexer.complete();

        var scanner = new RestResourceScanner();
        List<RestResourceDescriptor> descriptors = scanner.scan(index);
        descriptor = descriptors.get(0);
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
}
