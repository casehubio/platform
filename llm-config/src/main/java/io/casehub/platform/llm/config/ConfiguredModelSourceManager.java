package io.casehub.platform.llm.config;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.MutableModelRegistry;
import org.jboss.logging.Logger;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ConfiguredModelSourceManager {

    private static final Logger LOG = Logger.getLogger(ConfiguredModelSourceManager.class);

    private final MutableModelRegistry registry;
    private final LlmCredentialStore credentialStore;
    private final ConcurrentHashMap<String, ConfiguredModelSource> activeSources = new ConcurrentHashMap<>();

    public ConfiguredModelSourceManager(MutableModelRegistry registry, LlmCredentialStore credentialStore) {
        this.registry = registry;
        this.credentialStore = credentialStore;
    }

    public void configure(String tenancyId, String vendorKey, String credentialRef,
                          VendorClient client, List<ModelDescriptor> validatedModels) {
        String sourceKey = vendorKey + ":" + tenancyId;
        var source = new ConfiguredModelSource(tenancyId, vendorKey, credentialRef, client, credentialStore);
        List<ModelDescriptor> tenantScoped = source.toTenantScoped(validatedModels);
        activeSources.put(sourceKey, source);
        registry.replaceSource(source.sourceId(), source.priority(), tenantScoped);
    }

    public void unconfigure(String tenancyId, String vendorKey) {
        String sourceKey = vendorKey + ":" + tenancyId;
        ConfiguredModelSource removed = activeSources.remove(sourceKey);
        if (removed != null) {
            registry.replaceSource(removed.sourceId(), removed.priority(), List.of());
        }
    }

    public boolean isConfigured(String tenancyId, String vendorKey) {
        return activeSources.containsKey(vendorKey + ":" + tenancyId);
    }

    public ConfiguredModelSource getSource(String tenancyId, String vendorKey) {
        return activeSources.get(vendorKey + ":" + tenancyId);
    }

    public void refreshAll() {
        for (var entry : activeSources.entrySet()) {
            try {
                ConfiguredModelSource source = entry.getValue();
                List<ModelDescriptor> models = source.refresh();
                if (!activeSources.containsKey(entry.getKey())) {
                    continue;
                }
                registry.replaceSource(source.sourceId(), source.priority(), models);
            } catch (Exception e) {
                LOG.warnf("Configured source '%s' refresh failed: %s", entry.getKey(), e.getMessage());
            }
        }
    }
}
