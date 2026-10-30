package io.casehub.yaml.step.catalog;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StepKeywordsTest {

    @Test
    void reservedKeys_isUnionOfStructuralAndDecorator() {
        var expected = new LinkedHashSet<String>();
        expected.addAll(StepKeywords.STRUCTURAL_KEYS);
        expected.addAll(StepKeywords.DECORATOR_KEYS);

        assertThat(StepKeywords.RESERVED_KEYS).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void structuralAndDecoratorSets_areDisjoint() {
        var overlap = new LinkedHashSet<>(StepKeywords.STRUCTURAL_KEYS);
        overlap.retainAll(StepKeywords.DECORATOR_KEYS);

        assertThat(overlap)
                .as("keys must not appear in both STRUCTURAL_KEYS and DECORATOR_KEYS")
                .isEmpty();
    }

    @Test
    void decoratorKeys_includesAllDecoratorChainHandledKeys() {
        Set<String> decoratorChainKeys = Set.of(
                "when", "forEach", "background", "cancel", "loop",
                "on-error", "from", "timeout", "at", "wait",
                "retry", "semaphore", "mutex", "resource", "priority",
                "delay", "signal", "publish", "transition", "transform");

        assertThat(StepKeywords.DECORATOR_KEYS).containsAll(decoratorChainKeys);
    }
}
