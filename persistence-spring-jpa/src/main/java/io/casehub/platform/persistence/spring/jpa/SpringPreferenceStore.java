package io.casehub.platform.persistence.spring.jpa;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceChanged;
import io.casehub.platform.api.preferences.PreferencePermissions;
import io.casehub.platform.api.preferences.PreferenceQuery;
import io.casehub.platform.api.preferences.PreferenceRecord;
import io.casehub.platform.api.preferences.PreferenceStore;
import io.casehub.platform.persistence.jpa.PreferenceEntry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class SpringPreferenceStore implements PreferenceStore {

    private final PreferenceEntryRepository repo;
    private final CurrentPrincipal principal;
    private final ApplicationEventPublisher events;

    public SpringPreferenceStore(PreferenceEntryRepository repo,
                                 CurrentPrincipal principal,
                                 ApplicationEventPublisher events) {
        this.repo = repo;
        this.principal = principal;
        this.events = events;
    }

    @Override
    @Transactional
    public void set(String tenancyId, Path scope, String namespace, String name, String subKey, String value) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        String scopeValue = scope.value();
        PreferenceEntry existing = repo.findByTenancyIdAndScopeAndNamespaceAndNameAndSubKey(
                tenancyId, scopeValue, namespace, name, subKey).orElse(null);
        if (existing != null) {
            existing.value = value;
            repo.save(existing);
        } else {
            PreferenceEntry entry = new PreferenceEntry();
            entry.tenancyId = tenancyId;
            entry.scope = scopeValue;
            entry.namespace = namespace;
            entry.name = name;
            entry.subKey = subKey;
            entry.value = value;
            repo.save(entry);
        }
        events.publishEvent(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    @Transactional
    public void delete(String tenancyId, Path scope, String namespace, String name, String subKey) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        repo.deleteByKey(tenancyId, scope.value(), namespace, name, subKey);
        events.publishEvent(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PreferenceRecord> list(PreferenceQuery query) {
        String scopeValue = query.scope() != null ? query.scope().value() : null;
        List<PreferenceEntry> entries;
        if (scopeValue != null && query.namespace() != null) {
            entries = repo.findByTenancyIdAndScopeAndNamespace(query.tenancyId(), scopeValue, query.namespace());
        } else if (scopeValue != null) {
            entries = repo.findByTenancyIdAndScope(query.tenancyId(), scopeValue);
        } else if (query.namespace() != null) {
            entries = repo.findByTenancyIdAndNamespace(query.tenancyId(), query.namespace());
        } else {
            entries = repo.findByTenancyId(query.tenancyId());
        }
        return entries.stream()
                .map(e -> new PreferenceRecord(e.tenancyId, pathFromStored(e.scope), e.namespace, e.name, e.subKey, e.value))
                .toList();
    }

    @Override
    @Transactional
    public void deleteAll(String tenancyId, Path scope, String namespace) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        repo.deleteByNamespace(tenancyId, scope.value(), namespace);
        events.publishEvent(new PreferenceChanged(tenancyId, scope, namespace));
    }

    private static Path pathFromStored(String stored) {
        return stored.isEmpty() ? Path.root() : Path.parse(stored);
    }
}
