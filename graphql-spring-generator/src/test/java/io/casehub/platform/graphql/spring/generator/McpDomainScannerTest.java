package io.casehub.platform.graphql.spring.generator;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpDomainScannerTest {

    private static Index index;

    @BeforeAll
    static void buildIndex() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleDomainSpi.class);
        index = indexer.complete();
    }

    @Test
    void scansMcpDomainInterface() {
        var scanner = new McpDomainScanner();
        List<DomainDescriptor> descriptors = scanner.scan(index);

        assertThat(descriptors).hasSize(1);
        DomainDescriptor desc = descriptors.get(0);
        assertThat(desc.domainName()).isEqualTo("sample");
        assertThat(desc.spiInterfaceName()).isEqualTo(
                "io.casehub.platform.graphql.spring.generator.SampleDomainSpi");
    }

    @Test
    void extractsOperations() {
        var scanner = new McpDomainScanner();
        DomainDescriptor desc = scanner.scan(index).get(0);

        assertThat(desc.operations()).hasSize(3);

        DomainDescriptor.OperationDescriptor listItems = desc.operations().stream()
                .filter(o -> o.methodName().equals("listItems")).findFirst().orElseThrow();
        assertThat(listItems.operationType()).isEqualTo(DomainDescriptor.OperationType.QUERY);
        assertThat(listItems.description()).isEqualTo("List all items");
        assertThat(listItems.parameters()).hasSize(1);

        DomainDescriptor.OperationDescriptor createItem = desc.operations().stream()
                .filter(o -> o.methodName().equals("createItem")).findFirst().orElseThrow();
        assertThat(createItem.operationType()).isEqualTo(DomainDescriptor.OperationType.MUTATION);
        assertThat(createItem.parameters()).hasSize(2);
    }

    @Test
    void extractsReturnTypes() {
        var scanner = new McpDomainScanner();
        DomainDescriptor desc = scanner.scan(index).get(0);

        DomainDescriptor.OperationDescriptor listItems = desc.operations().stream()
                .filter(o -> o.methodName().equals("listItems")).findFirst().orElseThrow();
        assertThat(listItems.returnType().toString()).isEqualTo("java.util.List<java.lang.String>");

        DomainDescriptor.OperationDescriptor getItem = desc.operations().stream()
                .filter(o -> o.methodName().equals("getItem")).findFirst().orElseThrow();
        assertThat(getItem.returnType().toString()).isEqualTo("java.lang.String");
    }
}
