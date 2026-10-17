package io.casehub.platform.agent.config;

import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.platform.api.credentials.LlmCredentialStore;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.model.MutableModelRegistry;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AgentConfigLoader {

    private static final Logger LOG = Logger.getLogger(AgentConfigLoader.class);

    private final LlmCredentialStore credentialStore;
    private final MutableModelRegistry modelRegistry;
    private final CredentialResolver credentialResolver;
    private final Map<String, List<String>> vendorRequirements;
    private final LocalModelReconciler reconciler;
    private final String profile;
    private final Path projectDir;
    private final DIDResolver didResolver;
    private final boolean allowInsecure;

    public AgentConfigLoader(LlmCredentialStore credentialStore,
                             MutableModelRegistry modelRegistry,
                             CredentialResolver credentialResolver,
                             Map<String, List<String>> vendorRequirements,
                             LocalModelReconciler reconciler,
                             String profile,
                             Path projectDir,
                             DIDResolver didResolver,
                             boolean allowInsecure) {
        this.credentialStore = credentialStore;
        this.modelRegistry = modelRegistry;
        this.credentialResolver = credentialResolver;
        this.vendorRequirements = vendorRequirements;
        this.reconciler = reconciler;
        this.profile = profile;
        this.projectDir = projectDir;
        this.didResolver = didResolver;
        this.allowInsecure = allowInsecure;
    }

    public ManifestResult load() {
        var resolver = new ManifestCredentialResolver(credentialResolver);
        var securityConfig = new ManifestSecurityConfig(allowInsecure);
        var loader = new ManifestLoader(resolver, didResolver, securityConfig);
        var manifest = loader.load(projectDir, profile);

        var processor = new ManifestProcessor(
                credentialStore, modelRegistry, resolver, vendorRequirements, reconciler);
        var result = processor.process(manifest);

        LOG.infof("Agent config manifest processed: %d aliases, default backend: %s",
                result.aliases().size(),
                result.defaultBackendKey() != null ? result.defaultBackendKey() : "(not set)");

        return result;
    }

    public MutableModelRegistry modelRegistry() {
        return modelRegistry;
    }

    public static AgentConfigLoader withDefaults(Path projectDir) {
        return withDefaults(projectDir, null);
    }

    public static AgentConfigLoader withDefaults(Path projectDir, String profile) {
        return new AgentConfigLoader(
                new MapLlmCredentialStore(),
                new MapMutableModelRegistry(),
                ref -> Map.of(),
                Map.of(),
                model -> {},
                profile,
                projectDir,
                (actorId, did) -> java.util.Optional.empty(),
                false
        );
    }

    static final class MapLlmCredentialStore implements LlmCredentialStore {
        private final java.util.concurrent.ConcurrentHashMap<String, Map<String, String>> store =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void store(String tenancyId, String credentialRef, Map<String, String> credentials) {
            store.put(tenancyId + "::" + credentialRef, credentials);
        }

        @Override
        public Map<String, String> resolve(String tenancyId, String credentialRef) {
            return store.getOrDefault(tenancyId + "::" + credentialRef, Map.of());
        }

        @Override
        public void delete(String tenancyId, String credentialRef) {
            store.remove(tenancyId + "::" + credentialRef);
        }

        @Override
        public List<String> listRefs(String tenancyId) {
            String prefix = tenancyId + "::";
            return store.keySet().stream()
                        .filter(k -> k.startsWith(prefix))
                        .map(k -> k.substring(prefix.length()))
                        .toList();
        }
    }

    static final class MapMutableModelRegistry implements MutableModelRegistry {
        private final java.util.concurrent.ConcurrentHashMap<String, List<io.casehub.platform.api.model.ModelDescriptor>> sources =
                new java.util.concurrent.ConcurrentHashMap<>();
        private volatile java.util.concurrent.ConcurrentHashMap<String, io.casehub.platform.api.model.ModelDescriptor> view =
                new java.util.concurrent.ConcurrentHashMap<>();

        private void rebuildView() {
            var merged = new java.util.concurrent.ConcurrentHashMap<String, io.casehub.platform.api.model.ModelDescriptor>();
            sources.values().forEach(list -> list.forEach(m -> merged.put(m.id(), m)));
            this.view = merged;
        }

        @Override
        public java.util.Optional<io.casehub.platform.api.model.ModelDescriptor> resolveById(String modelId) {
            return java.util.Optional.ofNullable(view.get(modelId));
        }

        @Override
        public List<io.casehub.platform.api.model.ModelDescriptor> query(io.casehub.platform.api.model.ModelQuery query) {
            return view.values().stream()
                       .filter(d -> query.vendor() == null || query.vendor().equals(d.vendor()))
                       .filter(d -> query.tier() == null || query.tier() == d.tier())
                       .filter(d -> query.locality() == null || query.locality() == d.locality())
                       .filter(d -> d.capabilities().containsAll(query.requiredCapabilities()))
                       .toList();
        }

        @Override
        public List<io.casehub.platform.api.model.ModelDescriptor> all() {
            return List.copyOf(view.values());
        }

        @Override
        public CatalogDelta replaceSource(String sourceId, int priority, List<io.casehub.platform.api.model.ModelDescriptor> newModels) {
            var oldIds = new java.util.HashSet<>(view.keySet());
            sources.put(sourceId, List.copyOf(newModels));
            rebuildView();
            var newIds = new java.util.HashSet<>(view.keySet());
            var added  = new java.util.HashSet<>(newIds);
            added.removeAll(oldIds);
            var removed = new java.util.HashSet<>(oldIds);
            removed.removeAll(newIds);
            return new CatalogDelta(added, removed, java.util.Set.of());
        }
    }

}
