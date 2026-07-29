package io.casehub.platform.api.acl;

public record AclQuery(
        String actorId,
        String resourceType,
        AclAction action,
        String cursor,
        int limit
) {
    public AclQuery {
        if (limit <= 0) limit = 100;
        if (limit > 500) limit = 500;
    }
}
