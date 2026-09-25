package io.casehub.yaml.step;

import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;

public record CatalogEntry(
        String qualifiedName,
        StepDefinition definition,
        StepAction action) {}
