package io.casehub.platform.generator;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PaginatedResponse;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.PlatformStream;
import io.casehub.platform.api.mcp.RestName;
import io.casehub.platform.api.mcp.RestStatus;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@McpDomain("test-domain")
public interface SampleMcpDomain {

    @PlatformQuery("List all items")
    @PaginatedResponse
    List<String> listItems(String tenancyId, @RestName("page_size") int pageSize);

    @PlatformQuery("Get a single item")
    String getItem(@PathParam("id") String id);

    @PlatformMutation("Create a new item")
    @RolesAllowed("admin")
    @RestStatus(201)
    String createItem(String name);

    @PlatformStream("Watch item changes")
    Multi<String> watchItems(String tenancyId);
}
