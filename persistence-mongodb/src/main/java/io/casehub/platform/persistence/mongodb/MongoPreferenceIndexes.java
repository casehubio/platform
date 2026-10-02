package io.casehub.platform.persistence.mongodb;

import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@Startup
@ApplicationScoped
class MongoPreferenceIndexes {

    private final com.mongodb.client.MongoClient mongoClient;
    private final String                         database;

    @jakarta.inject.Inject
    MongoPreferenceIndexes(com.mongodb.client.MongoClient mongoClient,
                           @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.mongoClient = mongoClient;
        this.database    = database;
    }

    @PostConstruct
    void ensureIndexes() {
        mongoClient.getDatabase(database)
                   .getCollection(MongoPreferenceDocument.COLLECTION, MongoPreferenceDocument.class)
                   .createIndex(Indexes.ascending("scope"));
    }
}
