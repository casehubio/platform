package io.casehub.platform.agent.config;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import io.casehub.platform.api.model.CostTier;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelChain;
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
        var aliases = new LinkedHashMap<String, ModelChain>();
        for (var entry : manifest.aliases().entrySet()) {
            aliases.put(entry.getKey(), toModelChain(entry.getValue()));
        }

        String defaultBackend = manifest.defaults() != null ? manifest.defaults().backend() : null;
        var pools = new java.util.ArrayList<>(manifest.pools().values());

        var providers = new LinkedHashMap<String, Map<String, String>>();
        for (var pd : manifest.providers()) {
            var props = new LinkedHashMap<String, String>();
            if (pd.host() != null) props.put("host", pd.host());
            if (pd.hasScalarCredential()) props.put("credential", pd.scalarCredential());
            providers.put(pd.vendor(), props);
        }

        LOG.infof("Manifest processed: %d aliases, %d pools, %d providers, default backend: %s",
                aliases.size(), pools.size(), providers.size(),
                defaultBackend != null ? defaultBackend : "(not set)");
        return new ManifestResult(aliases, defaultBackend, pools, providers);
    }

    static ModelChain toModelChain(AliasDeclaration alias) {
        boolean hasQueryFields = alias.tier() != null || !alias.capabilities().isEmpty()
                || alias.locality() != null || alias.maxCost() != null
                || alias.minContext() != null || alias.minOutput() != null
                || alias.preferVendor() != null || alias.backendKey() != null;
        boolean hasChain = !alias.chain().isEmpty();

        if (hasQueryFields && hasChain) {
            throw new IllegalArgumentException(
                    "Alias has both 'chain' and query fields — these are mutually exclusive");
        }
        if (hasChain) {
            return ModelChain.of(alias.chain().stream()
                    .map(ManifestProcessor::toChainEntry)
                    .toList());
        }
        return ModelChain.of(List.of(new ModelChain.ModelChainEntry.Queried(toModelQuery(alias))));
    }

    private static ModelChain.ModelChainEntry toChainEntry(ChainEntry entry) {
        if (entry.model() != null) {
            return new ModelChain.ModelChainEntry.Named(entry.model());
        }
        return new ModelChain.ModelChainEntry.Queried(buildQuery(
                entry.tier(), entry.capabilities(), entry.locality(), entry.maxCost(),
                entry.minContext(), entry.minOutput(), entry.preferVendor(), entry.backendKey()));
    }

    static ModelQuery toModelQuery(AliasDeclaration alias) {
        return buildQuery(alias.tier(), alias.capabilities(), alias.locality(), alias.maxCost(),
                alias.minContext(), alias.minOutput(), alias.preferVendor(), alias.backendKey());
    }

    private static ModelQuery buildQuery(String tier, List<String> capabilities, String locality,
                                         String maxCost, Integer minContext, Integer minOutput,
                                         String preferVendor, String backendKey) {
        return ModelQuery.builder()
                .tier(tier != null ? ModelTier.valueOf(tier) : null)
                .requiredCapabilities(capabilities != null ? Set.copyOf(capabilities) : Set.of())
                .locality(locality != null ? ModelLocality.valueOf(locality) : null)
                .maxCostTier(maxCost != null ? CostTier.valueOf(maxCost) : null)
                .minContextWindow(minContext)
                .minMaxOutput(minOutput)
                .preferVendor(preferVendor)
                .backendKey(backendKey)
                .build();
    }
}
