package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;

@McpDomain("class-based")
@ApplicationScoped
public class ClassBasedDomainService {

    @PlatformQuery("Get status")
    public String getStatus() {
        return "ok";
    }

    @PlatformQuery("List all items")
    public java.util.List<String> listItems() {
        return java.util.List.of();
    }


    @PlatformMutation("Update status")
    public String updateStatus(String newStatus) {
        return newStatus;
    }

    public String internalHelper() {
        return "not exposed";
    }
}
