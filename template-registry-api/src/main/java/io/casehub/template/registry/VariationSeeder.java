package io.casehub.template.registry;

import java.util.List;

@FunctionalInterface
public interface VariationSeeder {

    List<String> seed(TemplateDefinition template, String language);
}
