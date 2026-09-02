package io.casehub.platform.signing.tenant;

public record TenantKeyStoreConfig(
        String keystorePath,
        String keystorePassword,
        String keystoreType,
        String keyAlias) {}
