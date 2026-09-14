package io.casehub.platform.api.acl;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;

import java.util.List;

@McpDomain("acl")
public interface AclApi {

    @PlatformMutation("Grant access to a resource")
    @RestPath("grants")
    void grant(AclEntryRequest input);

    @PlatformMutation("Grant access in batch")
    @RestPath("grants/batch")
    void grantBatch(List<AclEntryRequest> inputs);

    @PlatformMutation("Revoke a grant")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("grants")
    void revoke(String actorId, ResourceId resourceId, AclAction action);

    @PlatformMutation("Revoke grants in batch")
    @RestPath("grants/revoke-batch")
    void revokeBatch(List<AclEntryRequest> inputs);

    @PlatformMutation("Revoke all grants for an actor on a resource")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("grants/all")
    void revokeAll(String actorId, ResourceId resourceId);

    @PlatformMutation("Add a deny entry")
    @RestPath("denies")
    void deny(AclEntryRequest input);

    @PlatformMutation("Add deny entries in batch")
    @RestPath("denies/batch")
    void denyBatch(List<AclEntryRequest> inputs);

    @PlatformMutation("Remove a deny entry")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("denies")
    void removeDeny(String actorId, ResourceId resourceId, AclAction action);

    @PlatformMutation("Remove deny entries in batch")
    @RestPath("denies/revoke-batch")
    void removeDenyBatch(List<AclEntryRequest> inputs);

    @PlatformMutation("Register a parent resource relationship")
    @RestPath("parents")
    void registerParent(String childResourceId, String parentResourceId);

    @PlatformQuery("Check if an actor has access to a resource")
    @RestPath("check")
    AccessCheckResponse check(String actorId, ResourceId resourceId, AclAction action);

    @PlatformQuery("List accessible resources for an actor")
    @RestPath("accessible")
    AclPage accessible(String actorId, String resourceType, AclAction action, String cursor, Integer limit);
}
