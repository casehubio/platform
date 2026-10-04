package io.casehub.platform.agent.config;

import io.casehub.platform.api.model.ModelQuery;
import java.util.List;
import java.util.Map;

public record ManifestResult(Map<String, ModelQuery> aliases, String defaultBackendKey, List<PoolDeclaration> pools) {

    public ManifestResult {
        aliases = aliases != null ? Map.copyOf(aliases) : Map.of();
        pools = pools != null ? List.copyOf(pools) : List.of();
    }

    public static ManifestResult empty() {
        return new ManifestResult(Map.of(), null, List.of());
    }
}
