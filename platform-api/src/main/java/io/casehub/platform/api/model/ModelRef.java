package io.casehub.platform.api.model;

import java.util.Objects;

public final class ModelRef {

    private static final String TIER_PREFIX = "tier:";

    public static String forTier(ModelTier tier) {
        Objects.requireNonNull(tier, "tier");
        return TIER_PREFIX + tier.name();
    }

    public static boolean isTierRef(String model) {
        return model != null && model.startsWith(TIER_PREFIX);
    }

    public static ModelTier parseTier(String model) {
        return ModelTier.valueOf(model.substring(TIER_PREFIX.length()));
    }

    private ModelRef() {}
}
