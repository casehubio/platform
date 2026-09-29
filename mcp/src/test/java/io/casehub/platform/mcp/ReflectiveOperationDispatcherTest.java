package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpOperationResult;
import io.casehub.platform.api.mcp.OperationOutcome;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ReflectiveOperationDispatcherTest {

    @Inject
    ReflectiveOperationDispatcher dispatcher;

    @Test
    void dispatchesQueryOperation() throws Exception {
        Object result = dispatcher.dispatch("test", "echo",
                Map.of("message", "ping"));
        assertThat(result).isEqualTo("ping");
    }

    @Test
    void dispatchesNoArgQuery() throws Exception {
        Object result = dispatcher.dispatch("test", "hello", Map.of());
        assertThat(result).isEqualTo("Hello from CaseHub");
    }

    @Test
    void dispatchesMutationOperation() throws Exception {
        Object result = dispatcher.dispatch("test", "store",
                Map.of("key", "a", "value", "b"));
        assertThat(result).isEqualTo("a=b");
    }

    @Test
    void rejectsNonAnnotatedMethod() {
        assertThatThrownBy(() -> dispatcher.dispatch("test", "notExposed", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown operation");
    }

    @Test
    void rejectsUnknownDomain() {
        assertThatThrownBy(() -> dispatcher.dispatch("nonexistent", "echo", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown operation");
    }

    @Test
    void rejectsUnknownOperation() {
        assertThatThrownBy(() -> dispatcher.dispatch("test", "doesNotExist", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown operation");
    }

    @Test
    void handlesNullParams() throws Exception {
        Object result = dispatcher.dispatch("test", "hello", null);
        assertThat(result).isEqualTo("Hello from CaseHub");
    }

    @Test
    void rejectsMissingRequiredParam() {
        assertThatThrownBy(() -> dispatcher.dispatch("test", "echo", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required parameter 'message'")
                .hasMessageContaining("is missing")
                .hasMessageContaining("Expected:");
    }

    @Test
    void rejectsUnknownParamName() {
        assertThatThrownBy(() -> dispatcher.dispatch("test", "echo",
                                                     Map.of("msg", "hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown parameter 'msg'")
                .hasMessageContaining("Expected:");
    }

    @Test
    void errorMessageIncludesExpectedSchema() {
        assertThatThrownBy(() -> dispatcher.dispatch("test", "echo",
                                                     Map.of("wrong", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message: String (required)");
    }

    @Test
    void dispatchesToDirectDomainQuery() throws Exception {
        Object result = dispatcher.dispatch("direct", "lookup",
                                            Map.of("id", "abc"));
        assertThat(result).isEqualTo("found:abc");
    }

    @Test
    void dispatchesToDirectDomainMutation() throws Exception {
        Object result = dispatcher.dispatch("direct", "createItem",
                                            Map.of("name", "widget", "count", 5));
        assertThat(result).isEqualTo("widget:5");
    }

    @Test
    void catchesMcpCapabilityExceptionUnsupported() throws Exception {
        Object result = dispatcher.dispatch("cap-test", "search",
                                            Map.of("query", "test"));

        assertThat(result).isInstanceOf(McpOperationResult.class);
        McpOperationResult opResult = (McpOperationResult) result;
        assertThat(opResult.outcome()).isEqualTo(OperationOutcome.UNSUPPORTED);
        assertThat(opResult.domain()).isEqualTo("cap-test");
        assertThat(opResult.operation()).isEqualTo("search");
        assertThat(opResult.capability()).isEqualTo("SearchOperations");
        assertThat(opResult.provider()).isEqualTo("s3");
        assertThat(opResult.supportedCapabilities()).containsExactly("Upload", "Download", "Delete");
        assertThat(opResult.message()).isEqualTo("S3 does not support search");
    }

    @Test
    void catchesMcpCapabilityExceptionUnavailable() throws Exception {
        Object result = dispatcher.dispatch("cap-test", "checkStatus", Map.of());

        assertThat(result).isInstanceOf(McpOperationResult.class);
        McpOperationResult opResult = (McpOperationResult) result;
        assertThat(opResult.outcome()).isEqualTo(OperationOutcome.UNAVAILABLE);
        assertThat(opResult.domain()).isEqualTo("cap-test");
        assertThat(opResult.operation()).isEqualTo("checkStatus");
        assertThat(opResult.capability()).isEqualTo("StatusCheck");
        assertThat(opResult.provider()).isEqualTo("truelayer");
    }

    @Test
    void catchesForeignCapabilityExceptionViaReflection() throws Exception {
        Object result = dispatcher.dispatch("foreign-cap-test", "search",
                                            Map.of("query", "test"));

        assertThat(result).isInstanceOf(McpOperationResult.class);
        McpOperationResult opResult = (McpOperationResult) result;
        assertThat(opResult.outcome()).isEqualTo(OperationOutcome.UNSUPPORTED);
        assertThat(opResult.domain()).isEqualTo("foreign-cap-test");
        assertThat(opResult.operation()).isEqualTo("search");
        assertThat(opResult.capability()).isEqualTo("SearchOperations");
        assertThat(opResult.provider()).isEqualTo("s3-foreign");
        assertThat(opResult.supportedCapabilities()).containsExactly("Upload", "Download", "Delete");
        assertThat(opResult.message()).isEqualTo("Foreign S3 does not support search");
    }


}
