package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JQEvaluatorTest {

    @Inject JQEvaluator jqEvaluator;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void eval_identity_returns_input_node() {
        ObjectNode node = MAPPER.createObjectNode().put("value", 42);
        ValidationResult result = jqEvaluator.eval(".", node);
        assertTrue(result.ok());
        assertFalse(result.output().isEmpty());
    }

    @Test
    void eval_field_access_returns_field_value() {
        ObjectNode node = MAPPER.createObjectNode().put("name", "alice");
        ValidationResult result = jqEvaluator.eval(".name", node);
        assertTrue(result.ok());
        assertEquals("alice", result.output().get(0).asText());
    }

    @Test
    void eval_boolean_expression_isTrue_when_satisfied() {
        ObjectNode node = MAPPER.createObjectNode().put("score", 10);
        ValidationResult result = jqEvaluator.eval(".score > 5", node);
        assertTrue(result.ok());
        assertTrue(result.isTrue());
    }

    @Test
    void eval_boolean_expression_isTrue_false_when_not_satisfied() {
        ObjectNode node = MAPPER.createObjectNode().put("score", 3);
        ValidationResult result = jqEvaluator.eval(".score > 5", node);
        assertTrue(result.ok());
        assertFalse(result.isTrue());
    }

    @Test
    void eval_invalid_expression_returns_error() {
        ObjectNode node = MAPPER.createObjectNode();
        ValidationResult result = jqEvaluator.eval("this is not jq !!!", node);
        assertFalse(result.ok());
        assertNotNull(result.error());
    }

    @Test
    void eval_without_secrets_does_not_throw() {
        ObjectNode node = MAPPER.createObjectNode().put("x", 1);
        ValidationResult result = jqEvaluator.eval(".x", node);
        assertTrue(result.ok());
    }

    @Test
    void eval_injects_secret_scope_variable() {
        // casehub.platform.secrets.testservice.apiKey=sk-test-key set in application.properties
        ObjectNode node = MAPPER.createObjectNode();
        ValidationResult result = jqEvaluator.eval(
                "$secret.testservice.apiKey", node, Set.of("testservice"), Set.of());
        assertTrue(result.ok(), () -> "eval failed: " + result.error());
        assertEquals("sk-test-key", result.output().get(0).asText());
    }
}
