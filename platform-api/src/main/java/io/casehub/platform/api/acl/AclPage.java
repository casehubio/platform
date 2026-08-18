package io.casehub.platform.api.acl;

import java.util.List;

public record AclPage(
        List<ResourceId> resourceIds,
        String nextCursor
) {
    public AclPage {
        resourceIds = List.copyOf(resourceIds);
    }
}
