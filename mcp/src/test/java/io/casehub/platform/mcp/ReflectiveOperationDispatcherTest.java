package io.casehub.platform.mcp;

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


}
