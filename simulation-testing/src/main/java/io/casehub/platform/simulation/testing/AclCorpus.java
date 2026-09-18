package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.generated.AccessControlProviderQN;

public final class AclCorpus {

    private AclCorpus() {}

    public static CorpusSeed<Object[], Boolean> canAccess(String tenancyId) {
        return new CorpusSeed<Object[], Boolean>(AccessControlProviderQN.CANACCESS, tenancyId)
                .withKeyExtractor(canAccessExtractor());
    }

    public static Object[] check(String actorId, ResourceId resourceId, AclAction action) {
        return new Object[]{actorId, resourceId, action};
    }

    public static ResourceId resource(String type, String id) {
        return new ResourceId(type, id);
    }

    public static KeyExtractor<Object[]> canAccessExtractor() {
        return params -> params[0] + ":" + params[1] + ":" + params[2];
    }
}
