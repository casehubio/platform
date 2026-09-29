package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.platform.api.expression.ConfigManager;
import io.casehub.platform.api.expression.SecretManager;
import io.casehub.platform.api.expression.SecretNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class JQEvaluatorCoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private JQEvaluatorCore evaluator;

    @BeforeEach
    void setUp() {
        SecretManager secretManager = name -> {
            if ("testservice".equals(name)) {
                return Map.of("apiKey", "sk-test-key", "endpoint", "https://api.test.com");
            }
            throw new SecretNotFoundException(name);
        };
        ConfigManager configManager = new ConfigManagerCore(
                new ConfigManagerCoreTest.MapPropertySource(Map.of(
                        "myapp.timeout", "5000",
                        "myapp.retries", "3")));
        evaluator = new JQEvaluatorCore(secretManager, configManager);
    }

    @Test
    void eval_identity_returns_input() {
        ObjectNode node = MAPPER.createObjectNode().put("value", 42);
        ValidationResult result = evaluator.eval(".", node);
        assertThat(result.ok()).isTrue();
        assertThat(result.output()).isNotEmpty();
    }

    @Test
    void eval_field_access() {
        ObjectNode node = MAPPER.createObjectNode().put("name", "alice");
        ValidationResult result = evaluator.eval(".name", node);
        assertThat(result.ok()).isTrue();
        assertThat(result.output().get(0).asText()).isEqualTo("alice");
    }

    @Test
    void eval_injects_secret_scope() {
        ObjectNode node = MAPPER.createObjectNode();
        ValidationResult result = evaluator.eval(
                "$secret.testservice.apiKey", node, Set.of("testservice"), Set.of());
        assertThat(result.ok()).as(() -> "eval failed: " + result.error()).isTrue();
        assertThat(result.output().get(0).asText()).isEqualTo("sk-test-key");
    }

    @Test
    void eval_injects_config_scope() {
        ObjectNode node = MAPPER.createObjectNode();
        ValidationResult result = evaluator.eval(
                "$config.myapp.timeout", node, Set.of(), Set.of("myapp"));
        assertThat(result.ok()).as(() -> "eval failed: " + result.error()).isTrue();
        assertThat(result.output().get(0).asText()).isEqualTo("5000");
    }

    @Test
    void eval_invalid_expression_returns_error() {
        ObjectNode node = MAPPER.createObjectNode();
        ValidationResult result = evaluator.eval("this is not jq !!!", node);
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isNotNull();
    }

    @Test
    void eval_without_scopes_works() {
        ObjectNode node = MAPPER.createObjectNode().put("x", 1);
        ValidationResult result = evaluator.eval(".x", node);
        assertThat(result.ok()).isTrue();
    }
}
