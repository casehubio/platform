package io.casehub.platform.expression;

import io.casehub.platform.api.expression.SecretManager;
import io.casehub.platform.api.expression.SecretNotFoundException;

import java.util.*;

public class SecretManagerCore implements SecretManager {

    private static final String PREFIX = "casehub.platform.secrets.";
    private final PropertySource source;

    public SecretManagerCore(PropertySource source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public Map<String, Object> secret(String secretName) {
        String prefix = PREFIX + secretName + ".";
        Map<String, Object> result = new HashMap<>();
        for (String name : source.getPropertyNames()) {
            if (name.startsWith(prefix)) {
                source.getProperty(name)
                        .ifPresent(v -> PropertyMapBuilder.put(
                                result, name.substring(prefix.length()), v));
            }
        }
        if (result.isEmpty()) throw new SecretNotFoundException(secretName);
        return result;
    }
}
