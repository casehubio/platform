package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CollisionDetectorTest {

    private InMemoryTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryTemplateRegistry();
    }

    @Test
    void detect_findsCollisionWithOtherNamespace() {
        var ns1 = new TemplateNamespace("ns1", Map.of("t1",
            TemplateDefinition.builder("t1")
                .pattern("I ${action} this ${thing}")
                .variable("action", VariableConstraint.string())
                .variable("thing", VariableConstraint.string())
                .build()));
        registry.register(ns1);

        var collisions = CollisionDetector.detect(
            List.of("I ${action} this ${thing}"),
            Map.of("action", VariableConstraint.string(),
                   "thing", VariableConstraint.string()),
            "ns2", "t2", registry);

        assertThat(collisions).hasSize(1);
        assertThat(collisions.get(0).collidingNamespace()).isEqualTo("ns1");
        assertThat(collisions.get(0).collidingTemplate()).isEqualTo("t1");
    }

    @Test
    void detect_ignoresSelfMatch() {
        var ns = new TemplateNamespace("ns", Map.of("t1",
            TemplateDefinition.builder("t1")
                .pattern("I ${action} this ${thing}")
                .variable("action", VariableConstraint.string())
                .variable("thing", VariableConstraint.string())
                .build()));
        registry.register(ns);

        var collisions = CollisionDetector.detect(
            List.of("I ${action} this ${thing}"),
            Map.of("action", VariableConstraint.string(),
                   "thing", VariableConstraint.string()),
            "ns", "t1", registry);

        assertThat(collisions).isEmpty();
    }

    @Test
    void detect_usesEnumFirstValueForSubstitution() {
        var ns = new TemplateNamespace("ns", Map.of("vote",
            TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build()));
        registry.register(ns);

        var collisions = CollisionDetector.detect(
            List.of("I ${decision} this ${subject}"),
            Map.of("decision", VariableConstraint.enumOf("approve", "reject"),
                   "subject", VariableConstraint.string()),
            "ns2", "t2", registry);

        assertThat(collisions).hasSize(1);
    }

    @Test
    void detect_noCollisionWithDifferentPattern() {
        var ns = new TemplateNamespace("ns", Map.of("t1",
            TemplateDefinition.builder("t1")
                .pattern("I ${action} this ${thing}")
                .variable("action", VariableConstraint.string())
                .variable("thing", VariableConstraint.string())
                .build()));
        registry.register(ns);

        var collisions = CollisionDetector.detect(
            List.of("${thing} receives my ${action}"),
            Map.of("action", VariableConstraint.string(),
                   "thing", VariableConstraint.string()),
            "ns2", "t2", registry);

        assertThat(collisions).isEmpty();
    }

    @Test
    void detect_emptyVariationsReturnsEmpty() {
        var collisions = CollisionDetector.detect(
            List.of(), Map.of(), "ns", "t", registry);
        assertThat(collisions).isEmpty();
    }
}
