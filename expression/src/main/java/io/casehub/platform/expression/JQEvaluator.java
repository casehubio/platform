package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.expression.ConfigManager;
import io.casehub.platform.api.expression.SecretManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject SecretManager secretManager;
    @Inject ConfigManager configManager;

    private Scope rootScope;
    private final ConcurrentHashMap<String, JsonQuery> queryCache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        rootScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, rootScope);
    }

    /**
     * Evaluate a JQ expression without secret or config injection.
     */
    public ValidationResult eval(String jqExpr, JsonNode input) {
        return eval(jqExpr, input, Set.of(), Set.of());
    }

    /**
     * Evaluate a JQ expression with $secret and $config scope variables.
     *
     * @param jqExpr       JQ expression
     * @param input        input JSON node
     * @param secretNames  secret names to inject (from {@code use.secrets})
     * @param configMapNames config map names to inject (from {@code use.configMaps})
     */
    public ValidationResult eval(String jqExpr, JsonNode input,
                                 Set<String> secretNames, Set<String> configMapNames) {
        try {
            Scope childScope = Scope.newChildScope(rootScope);

            if (!secretNames.isEmpty()) {
                Map<String, Object> secretsMap = new HashMap<>();
                for (String name : secretNames) {
                    secretsMap.put(name, secretManager.secret(name));
                }
                childScope.setValue("secret", MAPPER.valueToTree(secretsMap));
            }

            if (!configMapNames.isEmpty()) {
                Map<String, Object> configsMap = new HashMap<>();
                for (String name : configMapNames) {
                    configsMap.put(name, configManager.configMap(name));
                }
                childScope.setValue("config", MAPPER.valueToTree(configsMap));
            }

            JsonQuery query = queryCache.computeIfAbsent(jqExpr,
                    expr -> {
                        try {
                            return JsonQuery.compile(expr, Versions.JQ_1_6);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

            List<JsonNode> out = new ArrayList<>();
            query.apply(childScope, input, out::add);
            return ValidationResult.ok(out);
        } catch (Exception e) {
            return ValidationResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
