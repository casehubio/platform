package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.StepCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImportScopedStepCatalogTest {

    private CatalogEntry entry(String name) {
        var def = new StepDefinition(name, null, Map.of(), Map.of(),
                new InvokeBinding.Mcp(name));
        return new CatalogEntry(name, def,
                (params, services) -> StepResult.of(Map.of()));
    }

    private StepCatalog globalCatalog() {
        return new StepCatalog() {
            @Override
            public Optional<CatalogEntry> resolve(String actionName) {
                if ("global-action".equals(actionName)) {
                    return Optional.of(entry("global-action"));
                }
                return Optional.empty();
            }

            @Override
            public Set<String> availableActions() {
                return Set.of("global-action");
            }
        };
    }

    @Test
    void importScopedShadowsGlobal() {
        var importedEntry = entry("local-override");
        var catalog = new ImportScopedStepCatalog(
                Map.of("global-action", importedEntry), globalCatalog());

        assertThat(catalog.resolve("global-action")).isPresent();
        assertThat(catalog.resolve("global-action").get().definition().name())
                .isEqualTo("local-override");
    }

    @Test
    void fallsBackToGlobalForUnimportedActions() {
        var catalog = new ImportScopedStepCatalog(
                Map.of("local-only", entry("local-only")), globalCatalog());

        assertThat(catalog.resolve("global-action")).isPresent();
        assertThat(catalog.resolve("global-action").get().definition().name())
                .isEqualTo("global-action");
    }

    @Test
    void availableActionsIncludesBothScopes() {
        var catalog = new ImportScopedStepCatalog(
                Map.of("local-only", entry("local-only")), globalCatalog());

        assertThat(catalog.availableActions())
                .containsExactlyInAnyOrder("local-only", "global-action");
    }

    @Test
    void resolveMissingFromBothReturnsEmpty() {
        var catalog = new ImportScopedStepCatalog(Map.of(), globalCatalog());

        assertThat(catalog.resolve("nonexistent")).isEmpty();
    }
}
