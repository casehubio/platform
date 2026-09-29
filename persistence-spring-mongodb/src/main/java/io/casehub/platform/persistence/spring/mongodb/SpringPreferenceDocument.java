package io.casehub.platform.persistence.spring.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "platform_preferences")
public class SpringPreferenceDocument {

    @Id
    public String id;
    @Indexed
    public String tenancyId;
    @Indexed
    public String scope;
    public String namespace;
    public String name;
    public String subKey = "";
    public String value;

    public static String compoundId(String tenancyId, String scope, String namespace,
                                    String name, String subKey) {
        return tenancyId + "|" + scope + "|" + namespace + "|" + name + "|" + subKey;
    }
}
