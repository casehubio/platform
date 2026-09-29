package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.expression.ConfigManager;
import io.casehub.platform.api.expression.SecretManager;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JQEvaluatorCore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecretManager secretManager;
    private final ConfigManager configManager;
    private final Scope rootScope;
    private final ConcurrentHashMap<String, JsonQuery> queryCache = new ConcurrentHashMap<>();

    public JQEvaluatorCore(SecretManager secretManager, ConfigManager configManager) {
        this.secretManager = Objects.requireNonNull(secretManager);
        this.configManager = Objects.requireNonNull(configManager);
        this.rootScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, rootScope);
    }

    public ValidationResult eval(String jqExpr, JsonNode input) {
        return eval(jqExpr, input, Set.of(), Set.of());
    }

    public ValidationResult eval(String jqExpr, JsonNode input,
                                 Set<String> secretNames, Set<String> configMapNames) {
        try {
            Scope childScope = Scope.newChildScope(rootScope);

            if (!secretNames.isEmpty()) {
                Map<String, Object> secretsMap = new HashMap<>();
                for (String name : secretNames) {
                    secretsMap.put(name, secretManager.secret(name));
                }
                JsonNode secretsNode = MAPPER.valueToTree(secretsMap);
                childScope.setValue("secret", secretsNode);
            }

            if (!configMapNames.isEmpty()) {
                Map<String, Object> configsMap = new HashMap<>();
                for (String name : configMapNames) {
                    configsMap.put(name, configManager.configMap(name));
                }
                JsonNode configsNode = MAPPER.valueToTree(configsMap);
                childScope.setValue("config", configsNode);
            }

            JsonQuery query = queryCache.computeIfAbsent(jqExpr, expr -> {
                try { return JsonQuery.compile(expr, Versions.JQ_1_6); }
                catch (Exception e) { throw new RuntimeException(e); }
            });

            List<JsonNode> out = new ArrayList<>();
            query.apply(childScope, input, out::add);
            return ValidationResult.ok(out);
        } catch (Exception e) {
            Throwable cause = (e instanceof RuntimeException && e.getCause() != null)
                    ? e.getCause() : e;
            return ValidationResult.error(
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }
}
