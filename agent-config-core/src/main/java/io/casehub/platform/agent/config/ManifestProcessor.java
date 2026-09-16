package io.casehub.platform.agent.config;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import io.casehub.platform.api.model.CostTier;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelTier;
import io.casehub.platform.api.model.MutableModelRegistry;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ManifestProcessor {

    private static final Logger LOG = Logger.getLogger(ManifestProcessor.class);
    private static final String PLATFORM_TENANT_ID = "platform";
    private static final int MANIFEST_SOURCE_PRIORITY = 8;

    private final LlmCredentialStore credentialStore;
    private final MutableModelRegistry modelRegistry;
    private final ManifestCredentialResolver credentialResolver;
    private final Map<String, List<String>> vendorRequiredFields;
    private final LocalModelReconciler reconciler;

    public ManifestProcessor(LlmCredentialStore credentialStore,
                             MutableModelRegistry modelRegistry,
                             ManifestCredentialResolver credentialResolver,
                             Map<String, List<String>> vendorRequiredFields,
                             LocalModelReconciler reconciler) {
        this.credentialStore = credentialStore;
        this.modelRegistry = modelRegistry;
        this.credentialResolver = credentialResolver;
        this.vendorRequiredFields = vendorRequiredFields;
        this.reconciler = reconciler;
    }

    public ManifestResult process(Manifest manifest) {
        processProviders(manifest);
        processModels(manifest);
        processLocalModels(manifest);
        return processAliasesAndDefaults(manifest);
    }

    private void processProviders(Manifest manifest) {
        for (var provider : manifest.providers()) {
            try {
                var resolved = resolveProviderCredentials(provider);
                if (!resolved.isEmpty()) {
                    var credRef = "cloud-" + provider.vendor();
                    credentialStore.store(PLATFORM_TENANT_ID, credRef, resolved);
                    LOG.infof("Stored credentials for vendor %s as %s", provider.vendor(), credRef);
                }
            } catch (Exception e) {
                LOG.warnf("Failed to resolve credentials for vendor %s: %s", provider.vendor(), e.getMessage());
            }
        }
    }

    private Map<String, String> resolveProviderCredentials(ProviderDeclaration provider) {
        if (provider.credential() == null) return Map.of();

        var requiredFields = vendorRequiredFields.getOrDefault(provider.vendor(), List.of());
        if (requiredFields.isEmpty()) return Map.of();

        if (provider.hasScalarCredential()) {
            if (requiredFields.size() != 1) {
                throw new IllegalArgumentException("Vendor " + provider.vendor()
                        + " requires " + requiredFields.size() + " credential fields but got scalar");
            }
            var ref = CredentialRef.parse((String) provider.credential());
            var value = credentialResolver.resolve(ref);
            return Map.of(requiredFields.get(0), value);
        }

        @SuppressWarnings("unchecked")
        var refMap = (Map<String, String>) provider.credential();
        for (var field : requiredFields) {
            if (!refMap.containsKey(field)) {
                throw new IllegalArgumentException("Missing required credential field '" + field
                        + "' for vendor " + provider.vendor());
            }
        }
        return credentialResolver.resolveMap(refMap);
    }

    private void processModels(Manifest manifest) {
        if (manifest.models().isEmpty()) return;
        var delta = modelRegistry.replaceSource("manifest", MANIFEST_SOURCE_PRIORITY, manifest.models());
        LOG.infof("Registered %d manifest models (added: %d, updated: %d, removed: %d)",
                manifest.models().size(), delta.addedIds().size(), delta.updatedIds().size(), delta.removedIds().size());
    }

    private void processLocalModels(Manifest manifest) {
        for (var local : manifest.localModels()) {
            if ("present".equals(local.ensure())) {
                try {
                    reconciler.ensurePresent(local.id());
                    LOG.infof("Local model reconciled: %s", local.id());
                } catch (Exception e) {
                    LOG.warnf("Failed to reconcile local model %s: %s", local.id(), e.getMessage());
                }
            }
        }
    }

    private ManifestResult processAliasesAndDefaults(Manifest manifest) {
        var aliases = new LinkedHashMap<String, ModelQuery>();
        for (var entry : manifest.aliases().entrySet()) {
            aliases.put(entry.getKey(), toModelQuery(entry.getValue()));
        }

        String defaultBackend = manifest.defaults() != null ? manifest.defaults().backend() : null;
        LOG.infof("Manifest processed: %d aliases, default backend: %s",
                aliases.size(), defaultBackend != null ? defaultBackend : "(not set)");
        return new ManifestResult(aliases, defaultBackend);
    }

    static ModelQuery toModelQuery(AliasDeclaration alias) {
        return ModelQuery.builder()
                .tier(alias.tier() != null ? ModelTier.valueOf(alias.tier()) : null)
                .requiredCapabilities(alias.capabilities() != null ? Set.copyOf(alias.capabilities()) : Set.of())
                .locality(alias.locality() != null ? ModelLocality.valueOf(alias.locality()) : null)
                .maxCostTier(alias.maxCost() != null ? CostTier.valueOf(alias.maxCost()) : null)
                .minContextWindow(alias.minContext())
                .minMaxOutput(alias.minOutput())
                .preferVendor(alias.preferVendor())
                .build();
    }
}
