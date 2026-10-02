package io.casehub.platform.persistence.mongodb;

import org.bson.codecs.pojo.annotations.BsonId;

public class MongoPreferenceDocument {

    static final String COLLECTION = "platform_preferences";

    @BsonId
    public String id;
    public String tenancyId;
    public String scope;
    public String namespace;
    public String name;
    public String subKey = "";
    public String value;

    public static String compoundId(final String tenancyId, final String scope, final String namespace,
                                    final String name, final String subKey) {
        return tenancyId + "|" + scope + "|" + namespace + "|" + name + "|" + subKey;
    }
}
