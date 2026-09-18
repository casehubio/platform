package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.model.ModelCapabilities;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelTier;
import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.platform.simulation.generated.ModelRegistryQN;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ModelCorpus {

    private ModelCorpus() {}

    public static CorpusSeed<String, Optional<ModelDescriptor>> resolveById(String tenancyId) {
        return new CorpusSeed<String, Optional<ModelDescriptor>>(ModelRegistryQN.RESOLVEBYID, tenancyId)
                .withKeyExtractor(id -> id);
    }

    public static Optional<ModelDescriptor> found(ModelDescriptor descriptor) {
        return Optional.of(descriptor);
    }

    public static ModelDescriptor model(String id, String backendKey, String vendor,
                                         String family, ModelTier tier, ModelLocality locality) {
        return new ModelDescriptor(id, id, backendKey, null, vendor, family, family,
                tier, Set.of(ModelCapabilities.TEXT), 128000, 4096, locality, null, null, Map.of());
    }
}
