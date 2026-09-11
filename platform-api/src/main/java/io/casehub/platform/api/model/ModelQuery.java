package io.casehub.platform.api.model;

import java.util.Set;

public record ModelQuery(
    String vendor,
    String family,
    ModelTier tier,
    Set<String> requiredCapabilities,
    ModelLocality locality,
    CostTier maxCostTier,
    String authMethod
) {
    public ModelQuery {
        requiredCapabilities = requiredCapabilities != null
            ? Set.copyOf(requiredCapabilities) : Set.of();
    }

    public static ModelQuery all() {
        return new ModelQuery(null, null, null, Set.of(), null, null, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String vendor;
        private String family;
        private ModelTier tier;
        private Set<String> requiredCapabilities = Set.of();
        private ModelLocality locality;
        private CostTier maxCostTier;
        private String authMethod;

        public Builder vendor(String vendor) { this.vendor = vendor; return this; }
        public Builder family(String family) { this.family = family; return this; }
        public Builder tier(ModelTier tier) { this.tier = tier; return this; }
        public Builder requiredCapabilities(Set<String> caps) {
            this.requiredCapabilities = caps; return this;
        }
        public Builder locality(ModelLocality locality) { this.locality = locality; return this; }
        public Builder maxCostTier(CostTier maxCostTier) { this.maxCostTier = maxCostTier; return this; }
        public Builder authMethod(String authMethod) { this.authMethod = authMethod; return this; }
        public ModelQuery build() {
            return new ModelQuery(vendor, family, tier, requiredCapabilities,
                locality, maxCostTier, authMethod);
        }
    }
}
