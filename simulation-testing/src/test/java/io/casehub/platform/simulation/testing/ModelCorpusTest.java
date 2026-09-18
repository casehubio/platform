package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelTier;
import io.casehub.platform.simulation.CorpusSeed;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCorpusTest {

    @Test
    void resolveByIdReturnsPreConfiguredSeed() {
        CorpusSeed<String, Optional<ModelDescriptor>> seed = ModelCorpus.resolveById("tenant-1");
        assertThat(seed.qualifiedName()).isEqualTo("model-registry.resolveById");
        assertThat(seed.keyExtractor()).isNotNull();
    }

    @Test
    void foundWrapsInOptional() {
        ModelDescriptor desc = ModelCorpus.model("test", "claude", "Anthropic", "Claude", ModelTier.FLAGSHIP, ModelLocality.CLOUD);
        Optional<ModelDescriptor> opt = ModelCorpus.found(desc);
        assertThat(opt).isPresent();
        assertThat(opt.get().id()).isEqualTo("test");
    }

    @Test
    void modelCreatesValidDescriptor() {
        ModelDescriptor desc = ModelCorpus.model("opus-5", "claude", "Anthropic", "Opus", ModelTier.FLAGSHIP, ModelLocality.CLOUD);
        assertThat(desc.id()).isEqualTo("opus-5");
        assertThat(desc.backendKey()).isEqualTo("claude");
        assertThat(desc.vendor()).isEqualTo("Anthropic");
        assertThat(desc.family()).isEqualTo("Opus");
        assertThat(desc.tier()).isEqualTo(ModelTier.FLAGSHIP);
        assertThat(desc.locality()).isEqualTo(ModelLocality.CLOUD);
    }

    @Test
    void keyExtractorUsesIdDirectly() {
        var seed = ModelCorpus.resolveById("tenant-1");
        seed.add("claude-opus-5", ModelCorpus.found(ModelCorpus.model("claude-opus-5", "claude", "Anthropic", "Opus", ModelTier.FLAGSHIP, ModelLocality.CLOUD)));
        assertThat(seed.build().get(0).key()).isEqualTo("claude-opus-5");
    }
}
