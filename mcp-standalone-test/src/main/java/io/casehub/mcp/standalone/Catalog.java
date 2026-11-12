package io.casehub.mcp.standalone;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.PlatformStream;

import java.util.List;
import java.util.concurrent.Flow;

@McpDomain(value = "sample/catalog", app = "sample", summary = "Standalone catalog")
public interface Catalog {
    @PlatformQuery("List catalog items")
    List<String> listItems();

    @PlatformMutation("Reset catalog")
    void reset();

    @PlatformStream("Watch catalog changes")
    Flow.Publisher<String> watchItems();
}
