package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CollisionDetector {

    private CollisionDetector() {}

    public record Collision(
        String variation,
        String collidingNamespace,
        String collidingTemplate
    ) {}

    public static List<Collision> detect(
            List<String> candidateVariations,
            Map<String, VariableConstraint> sourceVariables,
            String sourceNamespace,
            String sourceTemplate,
            UnifiedTemplateRegistry registry) {

        List<Collision> collisions = new ArrayList<>();
        for (String variation : candidateVariations) {
            String testInput = substituteTestValues(variation, sourceVariables);
            TemplateMatch result = registry.matchStructural(testInput);
            if (result instanceof TemplateMatch.Matched matched) {
                boolean isSelf = matched.namespace().equals(sourceNamespace)
                    && matched.templateName().equals(sourceTemplate);
                if (!isSelf) {
                    collisions.add(new Collision(
                        variation, matched.namespace(), matched.templateName()));
                }
            }
        }
        return List.copyOf(collisions);
    }

    private static String substituteTestValues(
            String variation,
            Map<String, VariableConstraint> variables) {
        String result = variation;
        for (var entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (!result.contains(placeholder)) continue;
            String testValue;
            if (entry.getValue() instanceof VariableConstraint.EnumConstraint ec) {
                testValue = ec.values().iterator().next();
            } else {
                testValue = "__collision_test_" + entry.getKey() + "__";
            }
            result = result.replace(placeholder, testValue);
        }
        return result;
    }
}
