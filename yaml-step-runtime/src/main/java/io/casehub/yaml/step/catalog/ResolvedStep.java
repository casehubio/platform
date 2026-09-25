package io.casehub.yaml.step.catalog;

import io.casehub.yaml.step.CatalogEntry;

import java.util.Map;

public sealed interface ResolvedStep permits ResolvedStep.PluginStep, ResolvedStep.InvokeStep {

    Map<String, Object> decorators();

    record PluginStep(
            CatalogEntry entry,
            Map<String, Object> params,
            Map<String, Object> decorators) implements ResolvedStep {

        public PluginStep {
            params = Map.copyOf(params);
            decorators = Map.copyOf(decorators);
        }
    }

    record InvokeStep(
            Map<String, Object> invokeSpec,
            Map<String, Object> decorators) implements ResolvedStep {

        public InvokeStep {
            invokeSpec = Map.copyOf(invokeSpec);
            decorators = Map.copyOf(decorators);
        }
    }
}
