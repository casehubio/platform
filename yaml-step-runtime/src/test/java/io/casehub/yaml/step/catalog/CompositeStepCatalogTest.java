package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeStepCatalogTest {

    private CatalogEntry entry(String name) {
        var def = new StepDefinition(name, null, Map.of(), Map.of(),
                new InvokeBinding.Mcp(name));
        return new CatalogEntry(name, def,
                (params, services) -> StepResult.of(Map.of()));
    }

    @Test
    void resolvesRegisteredAction() {
        CatalogSource source = new CatalogSource() {
            @Override
            public void populate(Map<String, CatalogEntry> entries) {
                entries.put("assess-risk", entry("assess-risk"));
            }
            @Override
            public int priority() { return 100; }
        };

        var catalog = new CompositeStepCatalog(List.of(source));
        catalog.initialize();

        assertThat(catalog.resolve("assess-risk")).isPresent();
        assertThat(catalog.resolve("assess-risk").get().qualifiedName()).isEqualTo("assess-risk");
    }

    @Test
    void resolveMissingActionReturnsEmpty() {
        var catalog = new CompositeStepCatalog(List.of());
        catalog.initialize();

        assertThat(catalog.resolve("nonexistent")).isEmpty();
    }

    @Test
    void availableActionsReturnsAllKeys() {
        CatalogSource source = new CatalogSource() {
            @Override
            public void populate(Map<String, CatalogEntry> entries) {
                entries.put("action-a", entry("action-a"));
                entries.put("action-b", entry("action-b"));
            }
            @Override
            public int priority() { return 100; }
        };

        var catalog = new CompositeStepCatalog(List.of(source));
        catalog.initialize();

        assertThat(catalog.availableActions()).containsExactlyInAnyOrder("action-a", "action-b");
    }

    @Test
    void firstSourceWinsOnNameCollision() {
        CatalogSource lowPriority = new CatalogSource() {
            @Override
            public void populate(Map<String, CatalogEntry> entries) {
                entries.put("shared", entry("from-low"));
            }
            @Override
            public int priority() { return 100; }
        };
        CatalogSource highPriority = new CatalogSource() {
            @Override
            public void populate(Map<String, CatalogEntry> entries) {
                entries.put("shared", entry("from-high"));
            }
            @Override
            public int priority() { return 200; }
        };

        var catalog = new CompositeStepCatalog(List.of(lowPriority, highPriority));
        catalog.initialize();

        assertThat(catalog.resolve("shared")).isPresent();
        assertThat(catalog.resolve("shared").get().definition().name()).isEqualTo("from-low");
    }
}
