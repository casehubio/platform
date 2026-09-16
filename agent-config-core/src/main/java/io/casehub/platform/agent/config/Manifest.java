package io.casehub.platform.agent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.casehub.platform.api.model.ModelDescriptor;
import java.util.List;
import java.util.Map;

public record Manifest(
        List<ModelDescriptor> models,
        List<ProviderDeclaration> providers,
        List<SourceDeclaration> sources,
        Map<String, AliasDeclaration> aliases,
        @JsonProperty("local-models") List<LocalModelDeclaration> localModels,
        ManifestDefaults defaults
) {
    public Manifest {
        models = models != null ? List.copyOf(models) : List.of();
        providers = providers != null ? List.copyOf(providers) : List.of();
        sources = sources != null ? List.copyOf(sources) : List.of();
        aliases = aliases != null ? Map.copyOf(aliases) : Map.of();
        localModels = localModels != null ? List.copyOf(localModels) : List.of();
    }
}
