package io.casehub.platform.persistence.mongodb;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceChanged;
import io.casehub.platform.api.preferences.PreferencePermissions;
import io.casehub.platform.api.preferences.PreferenceQuery;
import io.casehub.platform.api.preferences.PreferenceRecord;
import io.casehub.platform.api.preferences.PreferenceStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
@Alternative
@Priority(1)
public class MongoPreferenceStore implements PreferenceStore {

    private final com.mongodb.client.MongoClient mongoClient;
    private final String                         database;
    private final CurrentPrincipal               principal;
    private final Event<PreferenceChanged>       changedEvent;

    @Inject
    MongoPreferenceStore(com.mongodb.client.MongoClient mongoClient,
                         @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quarkus.mongodb.database") String database,
                         CurrentPrincipal principal,
                         Event<PreferenceChanged> changedEvent) {
        this.mongoClient  = mongoClient;
        this.database     = database;
        this.principal    = principal;
        this.changedEvent = changedEvent;
    }

    private com.mongodb.client.MongoCollection<MongoPreferenceDocument> collection() {
        return mongoClient.getDatabase(database)
                          .getCollection(MongoPreferenceDocument.COLLECTION, MongoPreferenceDocument.class);
    }

    @Override
    public void set(String tenancyId, Path scope, String namespace, String name, String subKey, String value) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        String                  id       = MongoPreferenceDocument.compoundId(tenancyId, scope.value(), namespace, name, subKey);
        var                     coll     = collection();
        MongoPreferenceDocument existing = coll.find(com.mongodb.client.model.Filters.eq("_id", id)).first();
        if (existing != null) {
            existing.value = value;
            coll.replaceOne(com.mongodb.client.model.Filters.eq("_id", id), existing);
        } else {
            MongoPreferenceDocument doc = new MongoPreferenceDocument();
            doc.id        = id;
            doc.tenancyId = tenancyId;
            doc.scope     = scope.value();
            doc.namespace = namespace;
            doc.name      = name;
            doc.subKey    = subKey;
            doc.value     = value;
            coll.insertOne(doc);
        }
        changedEvent.fireAsync(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    public void delete(String tenancyId, Path scope, String namespace, String name, String subKey) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        String id = MongoPreferenceDocument.compoundId(tenancyId, scope.value(), namespace, name, subKey);
        collection().deleteOne(com.mongodb.client.model.Filters.eq("_id", id));
        changedEvent.fireAsync(new PreferenceChanged(tenancyId, scope, namespace));
    }

    @Override
    public List<PreferenceRecord> list(PreferenceQuery query) {
        org.bson.conversions.Bson filter;
        if (query.scope() != null && query.namespace() != null) {
            filter = com.mongodb.client.model.Filters.and(
                    com.mongodb.client.model.Filters.eq("tenancyId", query.tenancyId()),
                    com.mongodb.client.model.Filters.eq("scope", query.scope().value()),
                    com.mongodb.client.model.Filters.eq("namespace", query.namespace()));
        } else if (query.scope() != null) {
            filter = com.mongodb.client.model.Filters.and(
                    com.mongodb.client.model.Filters.eq("tenancyId", query.tenancyId()),
                    com.mongodb.client.model.Filters.eq("scope", query.scope().value()));
        } else {
            filter = com.mongodb.client.model.Filters.eq("tenancyId", query.tenancyId());
        }
        List<MongoPreferenceDocument> docs = new java.util.ArrayList<>();
        collection().find(filter).into(docs);
        return docs.stream()
                   .map(d -> new PreferenceRecord(d.tenancyId, pathFromStored(d.scope), d.namespace, d.name, d.subKey, d.value))
                   .toList();
    }

    @Override
    public void deleteAll(String tenancyId, Path scope, String namespace) {
        PreferencePermissions.assertTenant(tenancyId, principal);
        collection().deleteMany(com.mongodb.client.model.Filters.and(
                com.mongodb.client.model.Filters.eq("tenancyId", tenancyId),
                com.mongodb.client.model.Filters.eq("scope", scope.value()),
                com.mongodb.client.model.Filters.eq("namespace", namespace)));
        changedEvent.fireAsync(new PreferenceChanged(tenancyId, scope, namespace));
    }

    private static Path pathFromStored(String stored) {
        return stored.isEmpty() ? Path.root() : Path.parse(stored);
    }
}
