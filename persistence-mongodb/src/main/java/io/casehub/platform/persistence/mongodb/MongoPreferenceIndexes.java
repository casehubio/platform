package io.casehub.platform.persistence.mongodb;

import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/** Ensures the {@code scope} index exists on {@code platform_preferences} at startup. */
@Startup
@ApplicationScoped
class MongoPreferenceIndexes {

    @PostConstruct
    void ensureIndexes() {
        MongoPreferenceDocument.mongoCollection().createIndex(Indexes.ascending("scope"));
    }
}
