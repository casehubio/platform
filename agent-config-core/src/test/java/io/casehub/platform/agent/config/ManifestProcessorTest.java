package io.casehub.platform.agent.config;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import io.casehub.platform.api.model.CostTier;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelTier;
import io.casehub.platform.api.model.MutableModelRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ManifestProcessorTest {

    private final TestCredentialStore credStore = new TestCredentialStore();
    private final TestModelRegistry registry = new TestModelRegistry();
    private final ManifestCredentialResolver credResolver = new ManifestCredentialResolver(ref -> Map.of());
    private final List<String> reconciledModels = new ArrayList<>();
    private final LocalModelReconciler reconciler = reconciledModels::add;

    private ManifestProcessor processor(Map<String, List<String>> vendorReqs) {
        return new ManifestProcessor(credStore, registry, credResolver, vendorReqs, reconciler);
    }

    @Test
    void processStoresCredentialsWithCloudPrefix() {
        var manifest = new Manifest(
                List.of(),
                List.of(new ProviderDeclaration("anthropic", "env:ANTHROPIC_API_KEY", null)),
                List.of(), Map.of(), List.of(), null);

        var envResolver = new ManifestCredentialResolver(ref -> Map.of()) {
            @Override
            public String resolve(CredentialRef ref) {
                return "sk-test-123";
            }
        };
        var proc = new ManifestProcessor(credStore, registry, envResolver,
                Map.of("anthropic", List.of("api-key")), reconciler);
        proc.process(manifest);

        var stored = credStore.resolve("platform", "cloud-anthropic");
        assertThat(stored).containsEntry("api-key", "sk-test-123");
    }

    @Test
    void processRegistersModelsAtPriority8() {
        var model = testModel("custom-model", ModelTier.STANDARD);
        var manifest = new Manifest(List.of(model), List.of(), List.of(), Map.of(), List.of(), null);

        processor(Map.of()).process(manifest);

        assertThat(registry.lastSourceId).isEqualTo("manifest");
        assertThat(registry.lastPriority).isEqualTo(8);
        assertThat(registry.models).containsKey("custom-model");
    }

    @Test
    void processConvertsAliasToModelQuery() {
        var alias = new AliasDeclaration("FLAGSHIP", List.of("reasoning", "code"), null, "HIGH", 128000, null, "anthropic");
        var manifest = new Manifest(List.of(), List.of(), List.of(), Map.of("reasoning-heavy", alias), List.of(), null);

        var result = processor(Map.of()).process(manifest);

        assertThat(result.aliases()).containsKey("reasoning-heavy");
        var query = result.aliases().get("reasoning-heavy");
        assertThat(query.tier()).isEqualTo(ModelTier.FLAGSHIP);
        assertThat(query.requiredCapabilities()).containsExactlyInAnyOrder("reasoning", "code");
        assertThat(query.maxCostTier()).isEqualTo(CostTier.HIGH);
        assertThat(query.minContextWindow()).isEqualTo(128000);
        assertThat(query.preferVendor()).isEqualTo("anthropic");
    }

    @Test
    void processReturnsDefaultBackend() {
        var manifest = new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(),
                new ManifestDefaults("ollama"));
        var result = processor(Map.of()).process(manifest);
        assertThat(result.defaultBackendKey()).isEqualTo("ollama");
    }

    @Test
    void processReturnsNullDefaultWhenNotSet() {
        var manifest = new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(), null);
        var result = processor(Map.of()).process(manifest);
        assertThat(result.defaultBackendKey()).isNull();
    }

    @Test
    void processCallsReconcilerForLocalModels() {
        var manifest = new Manifest(List.of(), List.of(), List.of(), Map.of(),
                List.of(new LocalModelDeclaration("llama-4-scout", "present")), null);
        processor(Map.of()).process(manifest);
        assertThat(reconciledModels).containsExactly("llama-4-scout");
    }

    @Test
    void processSkipsLocalModelWithoutEnsurePresent() {
        var manifest = new Manifest(List.of(), List.of(), List.of(), Map.of(),
                List.of(new LocalModelDeclaration("llama-4-scout", null)), null);
        processor(Map.of()).process(manifest);
        assertThat(reconciledModels).isEmpty();
    }

    @Test
    void processHandlesProviderWithNoCredential() {
        var manifest = new Manifest(List.of(),
                List.of(new ProviderDeclaration("ollama", null, "localhost:11434")),
                List.of(), Map.of(), List.of(), null);
        processor(Map.of("ollama", List.of())).process(manifest);
        assertThat(credStore.listRefs("platform")).isEmpty();
    }

    private ModelDescriptor testModel(String id, ModelTier tier) {
        return new ModelDescriptor(id, id, "backend", null, "vendor", "fam", id, tier,
                Set.of("text"), 200000, 16384, ModelLocality.CLOUD, CostTier.MEDIUM, "api-key", Map.of());
    }

    static class TestCredentialStore implements LlmCredentialStore {
        private final ConcurrentHashMap<String, Map<String, String>> store = new ConcurrentHashMap<>();

        @Override
        public void store(String tenancyId, String credentialRef, Map<String, String> credentials) {
            store.put(tenancyId + ":" + credentialRef, new HashMap<>(credentials));
        }

        @Override
        public Map<String, String> resolve(String tenancyId, String credentialRef) {
            return store.getOrDefault(tenancyId + ":" + credentialRef, Map.of());
        }

        @Override
        public void delete(String tenancyId, String credentialRef) {
            store.remove(tenancyId + ":" + credentialRef);
        }

        @Override
        public List<String> listRefs(String tenancyId) {
            return store.keySet().stream()
                    .filter(k -> k.startsWith(tenancyId + ":"))
                    .map(k -> k.substring(tenancyId.length() + 1))
                    .toList();
        }
    }

    static class TestModelRegistry implements MutableModelRegistry {
        final LinkedHashMap<String, ModelDescriptor> models = new LinkedHashMap<>();
        String lastSourceId;
        int lastPriority;

        @Override
        public CatalogDelta replaceSource(String sourceId, int priority, List<ModelDescriptor> newModels) {
            this.lastSourceId = sourceId;
            this.lastPriority = priority;
            models.clear();
            for (var m : newModels) models.put(m.id(), m);
            return new CatalogDelta(Set.copyOf(models.keySet()), Set.of(), Set.of());
        }

        @Override
        public Optional<ModelDescriptor> resolveById(String modelId) {
            return Optional.ofNullable(models.get(modelId));
        }

        @Override
        public List<ModelDescriptor> query(ModelQuery query) {
            return List.copyOf(models.values());
        }

        @Override
        public List<ModelDescriptor> all() {
            return List.copyOf(models.values());
        }
    }
}
