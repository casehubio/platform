package io.casehub.template.registry.llm;

import io.casehub.template.registry.CollisionDetector;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.UnifiedTemplateRegistry;
import io.casehub.template.registry.VariationSeeder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VariationSeedingService {

    private static final Logger LOG = Logger.getLogger(VariationSeedingService.class.getName());

    private final VariationSeeder seeder;
    private final UnifiedTemplateRegistry registry;

    public VariationSeedingService(VariationSeeder seeder, UnifiedTemplateRegistry registry) {
        this.seeder = Objects.requireNonNull(seeder, "seeder");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public TemplateNamespace seedAndRegister(TemplateNamespace namespace) {
        TemplateNamespace expanded = expandWithVariations(namespace);
        registry.register(expanded);
        return expanded;
    }

    private TemplateNamespace expandWithVariations(TemplateNamespace namespace) {
        Map<String, TemplateDefinition> expandedTemplates = new LinkedHashMap<>();
        for (var entry : namespace.templates().entrySet()) {
            TemplateDefinition def = entry.getValue();
            List<String> variations = seeder.seed(def, namespace.language());

            List<CollisionDetector.Collision> collisions = CollisionDetector.detect(
                variations, def.variables(), namespace.name(), def.name(), registry);

            Set<String> collidingVariations = collisions.stream()
                .map(CollisionDetector.Collision::variation)
                .collect(Collectors.toSet());

            List<String> safeVariations = variations.stream()
                .filter(v -> !collidingVariations.contains(v))
                .toList();

            for (var collision : collisions) {
                LOG.log(Level.WARNING, "Variation ''{0}'' for {1}:{2} collides with {3}:{4} — rejected",
                    new Object[]{collision.variation(), namespace.name(), def.name(),
                        collision.collidingNamespace(), collision.collidingTemplate()});
            }

            List<String> allPatterns = new ArrayList<>(def.patterns());
            allPatterns.addAll(safeVariations);
            expandedTemplates.put(def.name(),
                new TemplateDefinition(def.name(), allPatterns, def.variables(), def.messageFormat()));
        }
        return new TemplateNamespace(namespace.name(), expandedTemplates, namespace.language());
    }
}
