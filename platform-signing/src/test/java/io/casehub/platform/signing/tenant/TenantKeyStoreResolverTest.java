package io.casehub.platform.signing.tenant;

import io.casehub.platform.signing.document.KeyStoreManager;
import io.casehub.platform.signing.document.TestKeyStoreHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantKeyStoreResolverTest {

    @TempDir
    static Path tempDir;
    static Path defaultKs;

    @BeforeAll
    static void setup() throws Exception {
        defaultKs = TestKeyStoreHelper.createTestKeystore(tempDir);
    }

    @Test
    void resolve_noTenantConfig_fallsToDefault() {
        var defaultMgr = new KeyStoreManager(
                defaultKs.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
        var resolver = new TenantKeyStoreResolver(defaultMgr, Map.of());

        var mgr = resolver.resolve("tenant-acme");
        assertThat(mgr).isSameAs(defaultMgr);
    }

    @Test
    void resolve_tenantHasOwnKeystore_returnsTenantManager() throws Exception {
        Path tenantDir = Files.createDirectories(tempDir.resolve("tenant-acme"));
        Path tenantKs = TestKeyStoreHelper.createTestKeystore(tenantDir);
        var defaultMgr = new KeyStoreManager(
                defaultKs.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);

        var tenantConfig = new TenantKeyStoreConfig(
                tenantKs.toString(), TestKeyStoreHelper.PASSWORD, "PKCS12", TestKeyStoreHelper.ALIAS);
        var resolver = new TenantKeyStoreResolver(defaultMgr, Map.of("tenant-acme", tenantConfig));

        var mgr = resolver.resolve("tenant-acme");
        assertThat(mgr).isNotSameAs(defaultMgr);
        assertThat(mgr.isLoaded()).isTrue();
    }

    @Test
    void resolve_unknownTenant_fallsToDefault() throws Exception {
        Path tenantDir = Files.createDirectories(tempDir.resolve("tenant-other"));
        Path tenantKs = TestKeyStoreHelper.createTestKeystore(tenantDir);
        var defaultMgr = new KeyStoreManager(
                defaultKs.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);

        var tenantConfig = new TenantKeyStoreConfig(
                tenantKs.toString(), TestKeyStoreHelper.PASSWORD, "PKCS12", TestKeyStoreHelper.ALIAS);
        var resolver = new TenantKeyStoreResolver(defaultMgr, Map.of("tenant-other", tenantConfig));

        var mgr = resolver.resolve("unknown-tenant");
        assertThat(mgr).isSameAs(defaultMgr);
    }

    @Test
    void resolve_nullTenant_returnsDefault() {
        var defaultMgr = new KeyStoreManager(
                defaultKs.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
        var resolver = new TenantKeyStoreResolver(defaultMgr, Map.of());

        assertThat(resolver.resolve(null)).isSameAs(defaultMgr);
    }

    @Test
    void resolve_cachedPerTenant() throws Exception {
        Path tenantDir = Files.createDirectories(tempDir.resolve("tenant-cached"));
        Path tenantKs = TestKeyStoreHelper.createTestKeystore(tenantDir);
        var defaultMgr = new KeyStoreManager(
                defaultKs.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
        var tenantConfig = new TenantKeyStoreConfig(
                tenantKs.toString(), TestKeyStoreHelper.PASSWORD, "PKCS12", TestKeyStoreHelper.ALIAS);
        var resolver = new TenantKeyStoreResolver(defaultMgr, Map.of("t1", tenantConfig));

        var first = resolver.resolve("t1");
        var second = resolver.resolve("t1");
        assertThat(first).isSameAs(second);
    }
}
