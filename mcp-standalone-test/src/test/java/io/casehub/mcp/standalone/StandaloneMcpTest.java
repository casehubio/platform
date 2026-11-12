package io.casehub.mcp.standalone;

import io.casehub.platform.generator.McpDomainJandexScanner;
import io.casehub.platform.generator.OperationType;
import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.OperationDescriptor;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandaloneMcpTest {
    @Test
    void scanRegisterAndDiscoverWithoutPlatformApi() throws Exception {
        assertThatThrownBy(() -> Class.forName("io.casehub.platform.api.identity.TenancyConstants"))
                .isInstanceOf(ClassNotFoundException.class);

        var domains = new McpDomainJandexScanner().scan(Index.of(Catalog.class));
        assertThat(domains).hasSize(1);
        var scanned = domains.getFirst();
        assertThat(scanned.domainName()).isEqualTo("sample/catalog");
        assertThat(scanned.app()).isEqualTo("sample");
        assertThat(scanned.operations()).extracting(op -> op.type())
                .containsExactlyInAnyOrder(OperationType.QUERY, OperationType.MUTATION, OperationType.STREAM);

        var operations = scanned.operations().stream().map(op -> new OperationDescriptor(
                op.methodName(), OperationDescriptor.OperationType.valueOf(op.type().name()),
                op.description(), List.of(), op.returnTypeStr(), null, null)).toList();
        var registry = new DomainModelRegistry();
        registry.register(new DomainModel(scanned.domainName(), scanned.app(), scanned.summary(),
                operations, List.of(), Map.of()));

        assertThat(registry.listChildren("")).extracting(node -> node.fullPath()).containsExactly("sample");
        assertThat(registry.listChildren("sample")).singleElement().satisfies(node -> {
            assertThat(node.fullPath()).isEqualTo("sample/catalog");
            assertThat(node.isLeaf()).isTrue();
            assertThat(node.operationCount()).isEqualTo(3);
        });
        assertThat(registry.getOperation("sample/catalog", "reset")).get()
                .extracting(OperationDescriptor::type).isEqualTo(OperationDescriptor.OperationType.MUTATION);
        assertThat(registry.search("watch")).singleElement().satisfies(result -> {
            assertThat(result.domain()).isEqualTo("sample/catalog");
            assertThat(result.operation().name()).isEqualTo("watchItems");
        });
    }
}
