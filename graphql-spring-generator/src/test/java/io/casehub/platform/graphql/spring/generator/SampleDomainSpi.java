package io.casehub.platform.graphql.spring.generator;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;

@McpDomain("sample")
public interface SampleDomainSpi {

    @PlatformQuery("List all items")
    List<String> listItems(String tenancyId);

    @PlatformQuery("Get a single item by id")
    String getItem(String id);

    @PlatformMutation("Create a new item")
    String createItem(String name, String description);
}
