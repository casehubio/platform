package io.casehub.platform.agent.config;

import io.casehub.platform.api.model.ModelChain;
import java.util.List;
import java.util.Map;

public record ManifestResult(
        Map<String, ModelChain> aliases,
        String defaultBackendKey,
        List<PoolDeclaration> pools,
        Map<String, Map<String, String>> providers
) {

    public ManifestResult {
        aliases = aliases != null ? Map.copyOf(aliases) : Map.of();
        pools = pools != null ? List.copyOf(pools) : List.of();
        providers = providers != null
                ? providers.entrySet().stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> Map.copyOf(e.getValue())))
                : Map.of();
    }

    public static ManifestResult empty() {
        return new ManifestResult(Map.of(), null, List.of(), Map.of());
    }
}
