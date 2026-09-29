package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.platform.api.expression.ConfigManager;
import io.casehub.platform.api.expression.SecretManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * JQ expression evaluator with $secret and $config scope injection.
 *
 * <p>Canonical Foundation-tier JQ evaluator — all casehub repos must inject this bean
 * rather than instantiating {@code JsonQuery} directly. See protocol PP-20260522-jq-evaluation-canonical.
 *
 * <p>Scope variables available in expressions:
 * <ul>
 *   <li>{@code $secret.{name}.{property}} — resolved via {@link SecretManager}
 *   <li>{@code $config.{name}.{property}} — resolved via {@link ConfigManager}
 * </ul>
 */
@ApplicationScoped
public class JQEvaluator {

    @Inject SecretManager secretManager;
    @Inject ConfigManager configManager;

    private JQEvaluatorCore delegate;

    @PostConstruct
    void init() {
        delegate = new JQEvaluatorCore(secretManager, configManager);
    }

    public ValidationResult eval(String jqExpr, JsonNode input) {
        return delegate.eval(jqExpr, input);
    }

    public ValidationResult eval(String jqExpr, JsonNode input,
                                 Set<String> secretNames, Set<String> configMapNames) {
        return delegate.eval(jqExpr, input, secretNames, configMapNames);
    }
}
