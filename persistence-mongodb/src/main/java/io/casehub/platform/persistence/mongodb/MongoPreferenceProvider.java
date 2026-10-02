package io.casehub.platform.persistence.mongodb;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Alternative
@Priority(1)
public class MongoPreferenceProvider implements PreferenceProvider {

    private final com.mongodb.client.MongoClient mongoClient;
    private final String                         database;

    @jakarta.inject.Inject
    MongoPreferenceProvider(com.mongodb.client.MongoClient mongoClient,
                            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.mongoClient = mongoClient;
        this.database    = database;
    }

    private com.mongodb.client.MongoCollection<MongoPreferenceDocument> collection() {
        return mongoClient.getDatabase(database)
                          .getCollection(MongoPreferenceDocument.COLLECTION, MongoPreferenceDocument.class);
    }

    @Override
    public Preferences resolve(final SettingsScope scope) {
        final List<String> ancestors = ancestors(scope.scope());

        final List<MongoPreferenceDocument> docs;
        if (ancestors.isEmpty()) {
            docs = java.util.Collections.emptyList();
        } else {
            docs = new ArrayList<>();
            collection().find(com.mongodb.client.model.Filters.and(
                    com.mongodb.client.model.Filters.eq("tenancyId", scope.tenancyId()),
                    com.mongodb.client.model.Filters.in("scope", ancestors))).into(docs);
        }

        final Map<String, Integer> scopeOrder = new HashMap<>();
        for (int i = 0; i < ancestors.size(); i++) {
            scopeOrder.put(ancestors.get(i), i);
        }
        docs.sort((a, b) -> Integer.compare(
                scopeOrder.getOrDefault(a.scope, -1),
                scopeOrder.getOrDefault(b.scope, -1)));

        final Map<String, Object> merged = new HashMap<>();
        for (final MongoPreferenceDocument doc : docs) {
            final String mapKey = doc.subKey.isEmpty()
                                  ? doc.namespace + "." + doc.name
                                  : doc.namespace + "." + doc.name + "." + doc.subKey;
            merged.put(mapKey, doc.value);
        }

        return new MapPreferences(merged);
    }

    private static List<String> ancestors(final Path path) {
        final List<String> result  = new ArrayList<>();
        Path               current = path;
        while (current != null) {
            result.add(0, current.value());
            current = current.parent();
        }
        if (path.depth() > 0) {
            result.add(0, Path.root().value());
        }
        return result;
    }
}
