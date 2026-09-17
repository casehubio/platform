package io.casehub.platform.persistence.spring.jpa;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceQuery;
import io.casehub.platform.api.preferences.PreferenceRecord;
import io.casehub.platform.api.preferences.PreferenceStore;
import io.casehub.platform.persistence.jpa.PreferenceEntry;
import io.casehub.platform.testing.spring.SpringFixedCurrentPrincipal;
import io.casehub.platform.testing.spring.SpringTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({SpringPreferenceStoreTest.TestConfig.class, SpringTestConfig.class})
class SpringPreferenceStoreTest {

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = PreferenceEntryRepository.class)
    @EntityScan(basePackageClasses = PreferenceEntry.class)
    static class TestConfig {
        @Bean
        SpringPreferenceStore springPreferenceStore(
                PreferenceEntryRepository repo,
                SpringFixedCurrentPrincipal principal,
                ApplicationEventPublisher events) {
            return new SpringPreferenceStore(repo, principal, events);
        }

        @Bean
        SpringPreferenceProvider springPreferenceProvider(PreferenceEntryRepository repo) {
            return new SpringPreferenceProvider(repo);
        }
    }

    @Autowired PreferenceStore store;
    @Autowired SpringPreferenceProvider provider;

    @BeforeEach
    void setup() {
        store.set(TENANT, Path.root(), "ui", "theme", "", "dark");
    }

    @Test
    void setAndList() {
        List<PreferenceRecord> records = store.list(new PreferenceQuery(TENANT, Path.root(), "ui"));
        assertEquals(1, records.size());
        assertEquals("dark", records.getFirst().value());
    }

    @Test
    void updateOverwritesExisting() {
        store.set(TENANT, Path.root(), "ui", "theme", "", "light");
        List<PreferenceRecord> records = store.list(new PreferenceQuery(TENANT, Path.root(), "ui"));
        assertEquals(1, records.size());
        assertEquals("light", records.getFirst().value());
    }

    @Test
    void deleteRemovesEntry() {
        store.delete(TENANT, Path.root(), "ui", "theme", "");
        List<PreferenceRecord> records = store.list(new PreferenceQuery(TENANT, Path.root(), "ui"));
        assertTrue(records.isEmpty());
    }

    @Test
    void deleteAllRemovesNamespace() {
        store.set(TENANT, Path.root(), "ui", "lang", "", "en");
        store.deleteAll(TENANT, Path.root(), "ui");
        List<PreferenceRecord> records = store.list(new PreferenceQuery(TENANT, Path.root(), "ui"));
        assertTrue(records.isEmpty());
    }

    @Test
    void listByTenancyOnly() {
        store.set(TENANT, Path.root(), "other", "key", "", "val");
        List<PreferenceRecord> records = store.list(new PreferenceQuery(TENANT, null, null));
        assertEquals(2, records.size());
    }

    @Test
    void resolveWalksHierarchy() {
        store.set(TENANT, Path.parse("org/team"), "ui", "theme", "", "light");
        var prefs = provider.resolve(
                io.casehub.platform.api.preferences.SettingsScope.of(TENANT, Path.parse("org/team")));
        assertEquals("light", prefs.asMap().get("ui.theme"));
    }

    @Test
    void resolveInheritsFromParentScope() {
        var prefs = provider.resolve(
                io.casehub.platform.api.preferences.SettingsScope.of(TENANT, Path.parse("org/team")));
        assertEquals("dark", prefs.asMap().get("ui.theme"));
    }
}
