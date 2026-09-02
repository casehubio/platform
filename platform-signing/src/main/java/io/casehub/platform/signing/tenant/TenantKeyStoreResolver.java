package io.casehub.platform.signing.tenant;

import io.casehub.platform.signing.document.KeyStoreManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TenantKeyStoreResolver {

    private final KeyStoreManager defaultManager;
    private final Map<String, TenantKeyStoreConfig> tenantConfigs;
    private final ConcurrentHashMap<String, KeyStoreManager> cache = new ConcurrentHashMap<>();

    public TenantKeyStoreResolver(KeyStoreManager defaultManager,
                                   Map<String, TenantKeyStoreConfig> tenantConfigs) {
        this.defaultManager = defaultManager;
        this.tenantConfigs = tenantConfigs;
    }

    public KeyStoreManager resolve(String tenancyId) {
        if (tenancyId == null || !tenantConfigs.containsKey(tenancyId)) {
            return defaultManager;
        }
        return cache.computeIfAbsent(tenancyId, this::loadTenantKeyStore);
    }

    private KeyStoreManager loadTenantKeyStore(String tenancyId) {
        TenantKeyStoreConfig config = tenantConfigs.get(tenancyId);
        return new KeyStoreManager(
                config.keystorePath(), config.keystorePassword(),
                config.keystoreType(), config.keyAlias());
    }
}
