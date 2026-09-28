package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpCapabilityException;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@McpDomain("cap-test")
@ApplicationScoped
public class CapabilityExceptionTestResolver {

    @PlatformQuery("Search documents")
    public String search(String query) {
        throw McpCapabilityException.unsupported(
                "SearchOperations", "s3",
                List.of("Upload", "Download", "Delete"),
                "S3 does not support search");
    }

    @PlatformQuery("Check provider status")
    public String checkStatus() {
        throw McpCapabilityException.unavailable(
                "StatusCheck", "truelayer",
                "Provider auth expired");
    }
}
