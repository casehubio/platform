package io.casehub.platform.persistence.spring.mongodb;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceChanged;
import io.casehub.platform.api.preferences.PreferenceQuery;
import io.casehub.platform.api.preferences.PreferenceRecord;
import io.casehub.platform.testing.spring.SpringFixedCurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpringMongoPreferenceStoreTest {

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    @Mock PreferenceDocumentRepository repo;
    @Mock ApplicationEventPublisher events;

    SpringMongoPreferenceStore store;
    SpringMongoPreferenceProvider provider;

    @BeforeEach
    void setup() {
        var principal = new SpringFixedCurrentPrincipal();
        store = new SpringMongoPreferenceStore(repo, principal, events);
        provider = new SpringMongoPreferenceProvider(repo);
    }

    @Test
    void setSavesNewDocument() {
        when(repo.findById(any())).thenReturn(Optional.empty());

        store.set(TENANT, Path.root(), "ui", "theme", "", "dark");

        ArgumentCaptor<SpringPreferenceDocument> captor = ArgumentCaptor.forClass(SpringPreferenceDocument.class);
        verify(repo).save(captor.capture());
        SpringPreferenceDocument saved = captor.getValue();
        assertEquals(TENANT, saved.tenancyId);
        assertEquals("", saved.scope);
        assertEquals("ui", saved.namespace);
        assertEquals("theme", saved.name);
        assertEquals("dark", saved.value);
        verify(events).publishEvent(any(PreferenceChanged.class));
    }

    @Test
    void setUpdatesExistingDocument() {
        SpringPreferenceDocument existing = new SpringPreferenceDocument();
        existing.id = SpringPreferenceDocument.compoundId(TENANT, "", "ui", "theme", "");
        existing.tenancyId = TENANT;
        existing.scope = "";
        existing.namespace = "ui";
        existing.name = "theme";
        existing.value = "dark";
        when(repo.findById(existing.id)).thenReturn(Optional.of(existing));

        store.set(TENANT, Path.root(), "ui", "theme", "", "light");

        verify(repo).save(existing);
        assertEquals("light", existing.value);
    }

    @Test
    void deleteRemovesEntry() {
        store.delete(TENANT, Path.root(), "ui", "theme", "");

        verify(repo).deleteByTenancyIdAndScopeAndNamespaceAndNameAndSubKey(
                TENANT, "", "ui", "theme", "");
        verify(events).publishEvent(any(PreferenceChanged.class));
    }

    @Test
    void listByScopeAndNamespace() {
        SpringPreferenceDocument doc = new SpringPreferenceDocument();
        doc.tenancyId = TENANT;
        doc.scope = "";
        doc.namespace = "ui";
        doc.name = "theme";
        doc.subKey = "";
        doc.value = "dark";
        when(repo.findByTenancyIdAndScopeAndNamespace(TENANT, "", "ui"))
                .thenReturn(List.of(doc));

        List<PreferenceRecord> records = store.list(new PreferenceQuery(TENANT, Path.root(), "ui"));
        assertEquals(1, records.size());
        assertEquals("dark", records.getFirst().value());
    }

    @Test
    void listByTenancyOnly() {
        when(repo.findByTenancyId(TENANT)).thenReturn(List.of());

        List<PreferenceRecord> records = store.list(new PreferenceQuery(TENANT, null, null));
        assertTrue(records.isEmpty());
        verify(repo).findByTenancyId(TENANT);
    }

    @Test
    void deleteAllRemovesNamespace() {
        store.deleteAll(TENANT, Path.root(), "ui");

        verify(repo).deleteByTenancyIdAndScopeAndNamespace(TENANT, "", "ui");
        verify(events).publishEvent(any(PreferenceChanged.class));
    }

    @Test
    void resolveWalksHierarchy() {
        SpringPreferenceDocument rootDoc = new SpringPreferenceDocument();
        rootDoc.tenancyId = TENANT;
        rootDoc.scope = "";
        rootDoc.namespace = "ui";
        rootDoc.name = "theme";
        rootDoc.subKey = "";
        rootDoc.value = "dark";

        SpringPreferenceDocument childDoc = new SpringPreferenceDocument();
        childDoc.tenancyId = TENANT;
        childDoc.scope = "org/team";
        childDoc.namespace = "ui";
        childDoc.name = "theme";
        childDoc.subKey = "";
        childDoc.value = "light";

        when(repo.findByTenancyIdAndScopeIn(eq(TENANT), any()))
                .thenReturn(List.of(rootDoc, childDoc));

        var prefs = provider.resolve(
                io.casehub.platform.api.preferences.SettingsScope.of(TENANT, Path.parse("org/team")));
        assertEquals("light", prefs.asMap().get("ui.theme"));
    }

    @Test
    void resolveInheritsFromParentScope() {
        SpringPreferenceDocument rootDoc = new SpringPreferenceDocument();
        rootDoc.tenancyId = TENANT;
        rootDoc.scope = "";
        rootDoc.namespace = "ui";
        rootDoc.name = "theme";
        rootDoc.subKey = "";
        rootDoc.value = "dark";

        when(repo.findByTenancyIdAndScopeIn(eq(TENANT), any()))
                .thenReturn(List.of(rootDoc));

        var prefs = provider.resolve(
                io.casehub.platform.api.preferences.SettingsScope.of(TENANT, Path.parse("org/team")));
        assertEquals("dark", prefs.asMap().get("ui.theme"));
    }

    @Test
    void compoundIdFormat() {
        String id = SpringPreferenceDocument.compoundId("t1", "org/team", "ui", "theme", "");
        assertEquals("t1|org/team|ui|theme|", id);
    }
}
