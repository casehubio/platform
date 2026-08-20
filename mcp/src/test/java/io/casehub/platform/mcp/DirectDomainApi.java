package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain("direct")
public interface DirectDomainApi {

    @PlatformQuery("Look up by ID")
    String lookup(String id);

    @PlatformMutation("Create a new item")
    String createItem(String name, int count);

    String helper();
}
