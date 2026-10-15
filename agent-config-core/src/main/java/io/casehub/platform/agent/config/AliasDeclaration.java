package io.casehub.platform.agent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AliasDeclaration(
        String tier,
        List<String> capabilities,
        String locality,
        @JsonProperty("max-cost") String maxCost,
        @JsonProperty("min-context") Integer minContext,
        @JsonProperty("min-output") Integer minOutput,
        @JsonProperty("prefer-vendor") String preferVendor,
        @JsonProperty("backend-key") String backendKey,
        List<ChainEntry> chain
) {
    public AliasDeclaration {
        capabilities = capabilities != null ? List.copyOf(capabilities) : List.of();
        chain = chain != null ? List.copyOf(chain) : List.of();
    }
}
