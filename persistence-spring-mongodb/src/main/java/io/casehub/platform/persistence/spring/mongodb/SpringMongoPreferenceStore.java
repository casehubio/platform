package io.casehub.platform.persistence.spring.mongodb;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceChanged;
import io.casehub.platform.api.preferences.PreferencePermissions;
import io.casehub.platform.api.preferences.PreferenceQuery;
import io.casehub.platform.api.preferences.PreferenceRecord;
import io.casehub.platform.api.preferences.PreferenceStore;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

public class SpringMongoPreferenceStore implements PreferenceStore {

    private final PreferenceDocumentRepository repo;
    private final CurrentPrincipal principal;
    private final ApplicationEventPublisher events;

    public SpringMongoPreferenceStore(PreferenceDocumentRepository repo,
                                      CurrentPrincipal principal,
                                      ApplicationEventPublisher events) {
        this.repo = repo;
        this.principal = principal;
        this.events = events;
    }

    @Override
    public void set(String tenancyId, Path scope, String namespace, String name, String subKey, String value) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        String id = SpringPreferenceDocument.compoundId(tenancyId, scope.value(), namespace, name, subKey);
        SpringPreferenceDocument existing = repo.findById(id).orElse(null);
        if (existing != null) {
            existing.value = value;
            repo.save(existing);
        } else {
            SpringPreferenceDocument doc = new SpringPreferenceDocument();
            doc.id = id;
            doc.tenancyId = tenancyId;
            doc.scope = scope.value();
            doc.namespace = namespace;
            doc.name = name;
            doc.subKey = subKey;
            doc.value = value;
            repo.save(doc);
        }
        events.publishEvent(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    public void delete(String tenancyId, Path scope, String namespace, String name, String subKey) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        repo.deleteByTenancyIdAndScopeAndNamespaceAndNameAndSubKey(
                tenancyId, scope.value(), namespace, name, subKey);
        events.publishEvent(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    public List<PreferenceRecord> list(PreferenceQuery query) {
        List<SpringPreferenceDocument> docs;
        String scopeValue = query.scope() != null ? query.scope().value() : null;
        if (scopeValue != null && query.namespace() != null) {
            docs = repo.findByTenancyIdAndScopeAndNamespace(query.tenancyId(), scopeValue, query.namespace());
        } else if (scopeValue != null) {
            docs = repo.findByTenancyIdAndScope(query.tenancyId(), scopeValue);
        } else if (query.namespace() != null) {
            docs = repo.findByTenancyIdAndNamespace(query.tenancyId(), query.namespace());
        } else {
            docs = repo.findByTenancyId(query.tenancyId());
        }
        return docs.stream()
                .map(d -> new PreferenceRecord(d.tenancyId, pathFromStored(d.scope), d.namespace, d.name, d.subKey, d.value))
                .toList();
    }

    @Override
    public void deleteAll(String tenancyId, Path scope, String namespace) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        repo.deleteByTenancyIdAndScopeAndNamespace(tenancyId, scope.value(), namespace);
        events.publishEvent(new PreferenceChanged(tenancyId, scope, namespace));
    }

    private static Path pathFromStored(String stored) {
        return stored.isEmpty() ? Path.root() : Path.parse(stored);
    }
}
