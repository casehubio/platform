package io.casehub.platform.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GraphQLModelScannerTest {

    @Inject
    ModelRegistry registry;

    @Test
    void scansTestDomain() {
        var domain = registry.getDomain("test");
        assertThat(domain).isPresent();
        assertThat(domain.get().name()).isEqualTo("test");
    }

    @Test
    void discoversQueryOperations() {
        var domain = registry.getDomain("test").orElseThrow();
        assertThat(domain.queryCount()).isEqualTo(2);
    }

    @Test
    void discoversMutationOperations() {
        var domain = registry.getDomain("test").orElseThrow();
        assertThat(domain.mutationCount()).isEqualTo(2);}

    @Test
    void operationHasDescription() {
        var echo = registry.getOperation("test", "echo").orElseThrow();
        assertThat(echo.summary()).isEqualTo("Echo the input back");
    }

    @Test
    void operationHasParams() {
        var echo = registry.getOperation("test", "echo").orElseThrow();
        assertThat(echo.params()).hasSize(1);
        assertThat(echo.params().get(0).name()).isEqualTo("message");
        assertThat(echo.params().get(0).description()).isEqualTo("The message to echo");
    }

    @Test
    void nonAnnotatedMethodNotExposed() {
        var notExposed = registry.getOperation("test", "notExposed");
        assertThat(notExposed).isEmpty();
    }

    @Test
    void enricherMergesSummaryAndState() {
        var domain = registry.getDomain("test").orElseThrow();
        assertThat(domain.summary()).isEqualTo("Test domain for verification");
        assertThat(domain.state()).containsEntry("itemCount", 3);
    }

    @Test
    void operationStoresResolverClass() {
        var echo = registry.getOperation("test", "echo").orElseThrow();
        assertThat(echo.resolverClass()).isEqualTo(TestQueryResolver.class);
    }

    @Test
    void operationStoresMethod() {
        var echo = registry.getOperation("test", "echo").orElseThrow();
        assertThat(echo.method()).isNotNull();
        assertThat(echo.method().getName()).isEqualTo("echo");
    }

    @Test
    void kebabCaseConversion() {
        assertThat(GraphQLModelScanner.toKebabCase("caseLifecycle"))
                .isEqualTo("case-lifecycle");
        assertThat(GraphQLModelScanner.toKebabCase("workItemInboxUpdates"))
                .isEqualTo("work-item-inbox-updates");
        assertThat(GraphQLModelScanner.toKebabCase("hello"))
                .isEqualTo("hello");
    }

    @Test
    void discoversSubscriptionEvents() {
        var domain = registry.getDomain("test").orElseThrow();
        assertThat(domain.events()).hasSize(1);
        assertThat(domain.events().get(0).name()).isEqualTo("caseLifecycle");
    }

    @Test
    void eventHasChannelHint() {
        var domain = registry.getDomain("test").orElseThrow();
        assertThat(domain.events().get(0).channel()).isEqualTo("test-case-lifecycle");
    }

    @Test
    void eventHasDescription() {
        var domain = registry.getDomain("test").orElseThrow();
        assertThat(domain.events().get(0).summary()).isEqualTo("Case state changes");
    }

    @Test
    void complexTypeFieldExpansion() {
        var create = registry.getOperation("test", "create").orElseThrow();
        assertThat(create.params()).hasSize(1);
        var inputParam = create.params().get(0);
        assertThat(inputParam.name()).isEqualTo("input");
        assertThat(inputParam.typeName()).isEqualTo("TestInput");
        assertThat(inputParam.fields()).containsEntry("name", "String");
        assertThat(inputParam.fields()).containsEntry("count", "Integer");
    }

}
