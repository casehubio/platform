package io.casehub.platform.api.delivery;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.Map;

@McpDomain("digest")
public interface DigestApi {

    @PlatformQuery("Get digest status for the current user — pending notification counts per channel")
    Map<String, Integer> status();
}
