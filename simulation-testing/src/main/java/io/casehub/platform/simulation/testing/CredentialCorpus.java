package io.casehub.platform.simulation.testing;

import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.platform.simulation.generated.CredentialResolverQN;

import java.util.Map;

public final class CredentialCorpus {

    private CredentialCorpus() {}

    public static CorpusSeed<String, Map<String, String>> resolve(String tenancyId) {
        return new CorpusSeed<String, Map<String, String>>(CredentialResolverQN.RESOLVE, tenancyId)
                .withKeyExtractor(ref -> ref);
    }

    public static Map<String, String> credential(String key, String value) {
        return Map.of(key, value);
    }

    public static Map<String, String> credential(String k1, String v1, String k2, String v2) {
        return Map.of(k1, v1, k2, v2);
    }
}
