package io.casehub.platform.persistence.jpa;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceChanged;
import io.casehub.platform.api.preferences.PreferencePermissions;
import io.casehub.platform.api.preferences.PreferenceQuery;
import io.casehub.platform.api.preferences.PreferenceRecord;
import io.casehub.platform.api.preferences.PreferenceStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class JpaPreferenceStore implements PreferenceStore {

    @Inject CurrentPrincipal principal;
    @Inject Event<PreferenceChanged> changedEvent;
    @Inject EntityManager entityManager;

    @Override
    @Transactional
    public void set(String tenancyId, Path scope, String namespace, String name, String subKey, String value) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        String scopeValue = scope.value();
        PreferenceEntry existing = entityManager.createQuery(
                "from PreferenceEntry where tenancyId = ?1 and scope = ?2 and namespace = ?3 and name = ?4 and subKey = ?5",
                PreferenceEntry.class)
                .setParameter(1, tenancyId).setParameter(2, scopeValue)
                .setParameter(3, namespace).setParameter(4, name).setParameter(5, subKey)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            existing.value = value;
        } else {
            PreferenceEntry entry = new PreferenceEntry();
            entry.tenancyId = tenancyId;
            entry.scope = scopeValue;
            entry.namespace = namespace;
            entry.name = name;
            entry.subKey = subKey;
            entry.value = value;
            entityManager.persist(entry);
        }
        changedEvent.fireAsync(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    @Transactional
    public void delete(String tenancyId, Path scope, String namespace, String name, String subKey) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        entityManager.createQuery(
                "delete from PreferenceEntry where tenancyId = ?1 and scope = ?2 and namespace = ?3 and name = ?4 and subKey = ?5")
                .setParameter(1, tenancyId).setParameter(2, scope.value())
                .setParameter(3, namespace).setParameter(4, name).setParameter(5, subKey)
                .executeUpdate();
        changedEvent.fireAsync(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    public List<PreferenceRecord> list(PreferenceQuery query) {
        String scopeValue = query.scope() != null ? query.scope().value() : null;
        List<PreferenceEntry> entries;
        if (scopeValue != null && query.namespace() != null) {
            entries = entityManager.createQuery("from PreferenceEntry where tenancyId = ?1 and scope = ?2 and namespace = ?3", PreferenceEntry.class)
                    .setParameter(1, query.tenancyId()).setParameter(2, scopeValue).setParameter(3, query.namespace()).getResultList();
        } else if (scopeValue != null) {
            entries = entityManager.createQuery("from PreferenceEntry where tenancyId = ?1 and scope = ?2", PreferenceEntry.class)
                    .setParameter(1, query.tenancyId()).setParameter(2, scopeValue).getResultList();
        } else if (query.namespace() != null) {
            entries = entityManager.createQuery("from PreferenceEntry where tenancyId = ?1 and namespace = ?2", PreferenceEntry.class)
                    .setParameter(1, query.tenancyId()).setParameter(2, query.namespace()).getResultList();
        } else {
            entries = entityManager.createQuery("from PreferenceEntry where tenancyId = ?1", PreferenceEntry.class)
                    .setParameter(1, query.tenancyId()).getResultList();
        }
        return entries.stream()
                .map(e -> new PreferenceRecord(e.tenancyId, pathFromStored(e.scope), e.namespace, e.name, e.subKey, e.value))
                .toList();
    }

    @Override
    @Transactional
    public void deleteAll(String tenancyId, Path scope, String namespace) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        entityManager.createQuery("delete from PreferenceEntry where tenancyId = ?1 and scope = ?2 and namespace = ?3")
                .setParameter(1, tenancyId).setParameter(2, scope.value()).setParameter(3, namespace)
                .executeUpdate();
        changedEvent.fireAsync(new PreferenceChanged(tenancyId, scope, namespace));
    }

    private static Path pathFromStored(String stored) {
        return stored.isEmpty() ? Path.root() : Path.parse(stored);
    }
}
