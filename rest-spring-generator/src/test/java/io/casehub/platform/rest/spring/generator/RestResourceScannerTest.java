package io.casehub.platform.rest.spring.generator;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestResourceScannerTest {

    private static Index index;

    @BeforeAll
    static void buildIndex() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleResource.class);
        indexer.indexClass(SampleCore.class);
        index = indexer.complete();
    }

    @Test
    void scansPathAnnotatedClass() {
        var scanner = new RestResourceScanner();
        List<RestResourceDescriptor> descriptors = scanner.scan(index);

        assertThat(descriptors).hasSize(1);
        RestResourceDescriptor desc = descriptors.get(0);
        assertThat(desc.className()).isEqualTo("io.casehub.platform.rest.spring.generator.SampleResource");
        assertThat(desc.path()).isEqualTo("/items");
    }

    @Test
    void extractsMethods() {
        var scanner = new RestResourceScanner();
        List<RestResourceDescriptor> descriptors = scanner.scan(index);
        RestResourceDescriptor desc = descriptors.get(0);

        assertThat(desc.methods()).hasSize(4);

        RestMethodDescriptor listItems = desc.methods().stream()
                .filter(m -> m.methodName().equals("listItems")).findFirst().orElseThrow();
        assertThat(listItems.httpMethod()).isEqualTo("GET");
        assertThat(listItems.subPath()).isEmpty();

        RestMethodDescriptor getItem = desc.methods().stream()
                .filter(m -> m.methodName().equals("getItem")).findFirst().orElseThrow();
        assertThat(getItem.httpMethod()).isEqualTo("GET");
        assertThat(getItem.subPath()).isEqualTo("/{id}");

        RestMethodDescriptor createItem = desc.methods().stream()
                .filter(m -> m.methodName().equals("createItem")).findFirst().orElseThrow();
        assertThat(createItem.httpMethod()).isEqualTo("POST");

        RestMethodDescriptor deleteItem = desc.methods().stream()
                .filter(m -> m.methodName().equals("deleteItem")).findFirst().orElseThrow();
        assertThat(deleteItem.httpMethod()).isEqualTo("DELETE");
        assertThat(deleteItem.subPath()).isEqualTo("/{id}");
    }

    @Test
    void identifiesDelegationTarget() {
        var scanner = new RestResourceScanner();
        List<RestResourceDescriptor> descriptors = scanner.scan(index);
        RestResourceDescriptor desc = descriptors.get(0);

        assertThat(desc.delegateTypeName())
                .isEqualTo("io.casehub.platform.rest.spring.generator.SampleCore");
        assertThat(desc.delegateFieldName()).isEqualTo("core");
    }

    @Test
    void extractsParameterSources() {
        var scanner = new RestResourceScanner();
        RestResourceDescriptor desc = scanner.scan(index).get(0);

        RestMethodDescriptor listItems = desc.methods().stream()
                .filter(m -> m.methodName().equals("listItems")).findFirst().orElseThrow();
        assertThat(listItems.parameters()).hasSize(1);
        assertThat(listItems.parameters().get(0).source()).isEqualTo(RestMethodDescriptor.ParameterSource.QUERY);
        assertThat(listItems.parameters().get(0).annotationValue()).isEqualTo("tenancyId");

        RestMethodDescriptor getItem = desc.methods().stream()
                .filter(m -> m.methodName().equals("getItem")).findFirst().orElseThrow();
        assertThat(getItem.parameters()).hasSize(1);
        assertThat(getItem.parameters().get(0).source()).isEqualTo(RestMethodDescriptor.ParameterSource.PATH);
        assertThat(getItem.parameters().get(0).annotationValue()).isEqualTo("id");

        RestMethodDescriptor createItem = desc.methods().stream()
                .filter(m -> m.methodName().equals("createItem")).findFirst().orElseThrow();
        assertThat(createItem.parameters()).hasSize(1);
        assertThat(createItem.parameters().get(0).source()).isEqualTo(RestMethodDescriptor.ParameterSource.BODY);
    }

    @Test
    void extractsClassLevelMediaTypes() {
        var scanner = new RestResourceScanner();
        RestResourceDescriptor desc = scanner.scan(index).get(0);

        assertThat(desc.classConsumes()).containsExactly("application/json");
        assertThat(desc.classProduces()).containsExactly("application/json");
    }
}
